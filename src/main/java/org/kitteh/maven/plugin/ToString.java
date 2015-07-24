/*
 * * Copyright (C) 2015 Matt Baxter http://kitteh.org
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.kitteh.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Mojo(name = "tostring", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.COMPILE)
public class ToString extends AbstractMojo {
    @Parameter(property = "project.compileClasspathElements", required = true, readonly = true)
    private List<String> classpath;

    @Parameter(required = true)
    private String packageName;

    @Parameter(defaultValue = "false")
    private boolean toStringRequired;

    @Parameter(defaultValue = "false")
    private boolean toStringIgnoreUtilities;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Set<URL> urlSet = new HashSet<>();
            for (String classPath : this.classpath) {
                urlSet.add(new File(classPath).toURI().toURL());
            }
            ClassLoader contextClassLoader = URLClassLoader.newInstance(urlSet.toArray(new URL[urlSet.size()]), Thread.currentThread().getContextClassLoader());
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Classpath failure! Malformed URL.", e);
        }

        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setScanners(new SubTypesScanner(false), new ResourcesScanner());
        configurationBuilder.setUrls(ClasspathHelper.forClassLoader(new ClassLoader[]{ClasspathHelper.contextClassLoader(), ClasspathHelper.staticClassLoader()}));
        configurationBuilder.filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix(this.packageName)));
        Reflections reflections = new Reflections(configurationBuilder);

        Set<Class<?>> allClasses = reflections.getSubTypesOf(Object.class);

        List<String> problematic = new LinkedList<>();
        for (Class<?> clazz : allClasses) {
            if ((clazz.getModifiers() & Modifier.INTERFACE) != 0) {
                continue;
            }
            try {
                Method toString = clazz.getMethod("toString");
                if (toString.getDeclaringClass().equals(Object.class)) {
                    if (this.toStringIgnoreUtilities && isUtilityClass(clazz)) {
                        continue;
                    }
                    problematic.add(clazz.getName());
                }
            } catch (NoSuchMethodException e) {
                throw new MojoExecutionException("Could not find a toString at all on " + clazz.getName());
            }
        }

        if (!problematic.isEmpty()) {
            Collections.sort(problematic);
            this.getLog().warn("Found " + problematic.size() + " classes with Object's toString()");
            for (String name : problematic) {
                this.getLog().info(name);
            }
            if (this.toStringRequired) {
                throw new MojoFailureException("All classes must have a toString not from Object");
            }
        }
    }

    private boolean isUtilityClass(Class<?> clazz) {
        if (!(clazz.getSuperclass() == null || clazz.getSuperclass() == Object.class)) {
            return false;
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                return false;
            }
        }
        for (Field field : clazz.getDeclaredFields()) {
            if (!(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()))) {
                return false;
            }
        }
        return true;
    }
}