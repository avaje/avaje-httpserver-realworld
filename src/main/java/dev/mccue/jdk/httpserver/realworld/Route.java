package dev.mccue.jdk.httpserver.realworld;

import org.intellij.lang.annotations.Language;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Mark a route for reflective registration. Since methods are discovered
/// in arbitrary order by reflection, this is not a good idea if you have
/// overlapping regexes and methods. Maybe it would work, maybe not!
///
/// This is why it's not in the regexrouter library itself. I am still pondering
/// how best to handle it but rest assured I do see the value of route params being
/// next to the method declaration.
///
/// The solution is for something like `rut` to just get better. Or to find/jerry rig
/// a better router.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Route {
    String[] methods();

    @Language("RegExp") String pattern();
}