package eu.f3rog.blade.compiler.builder;

import android.content.Context;
import android.content.Intent;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import eu.f3rog.blade.compiler.name.GCN;
import eu.f3rog.blade.compiler.name.GPN;
import eu.f3rog.blade.compiler.util.ClassNameComparator;
import eu.f3rog.blade.compiler.util.ProcessorError;
import eu.f3rog.blade.core.BundleWrapper;

import static eu.f3rog.blade.compiler.util.ProcessorUtils.isSubClassOf;

/**
 * Class {@link NavigatorBuilder}
 *
 * @author FrantisekGazo
 * @version 2015-10-21
 */
public class NavigatorBuilder extends BaseClassBuilder {

    private static final String METHOD_NAME_FOR = "for%s";
    private static final String METHOD_NAME_START = "start%s";

    public NavigatorBuilder() throws ProcessorError {
        super(GCN.NAVIGATOR, GPN.BLADE);
    }

    @Override
    public void start() throws ProcessorError {
        super.start();
        getBuilder().addModifiers(Modifier.FINAL, Modifier.PUBLIC);
    }

    public void integrate(Map<ClassName, ActivityInjectorBuilder> aibs) throws ProcessorError {
        // get all class names
        List<ClassName> classes = new ArrayList<>(aibs.keySet());
        // sort them
        Collections.sort(classes, new ClassNameComparator());

        ActivityInjectorBuilder aib;
        List<VariableElement> extras = new ArrayList<>();
        for (int i = 0, size = classes.size(); i < size; i++) {
            aib = aibs.get(classes.get(i));

            TypeElement fragmentClass = (TypeElement) aib.getExtras().get(0).getEnclosingElement();
            // do not create method for abstract class
            if (fragmentClass.getModifiers().contains(Modifier.ABSTRACT)) continue;
            // get all inherited extras
            for (int j = 0; j < size; j++) {
                if (isSubClassOf(fragmentClass, classes.get(j))) {
                    extras.addAll(aibs.get(classes.get(j)).getExtras());
                }
            }
            // get all declared extras
            extras.addAll(aib.getExtras());
            // integrate
            integrate(aib, extras);

            // clear extras
            extras.clear();
        }
    }

    private void integrate(ActivityInjectorBuilder aib, List<VariableElement> allExtras) throws ProcessorError {
        ClassName activityClassName = aib.getArgClassName();
        String forName = getMethodName(METHOD_NAME_FOR, activityClassName);
        String context = "context";
        String intent = "intent";
        String extras = "extras";
        // build FOR method
        MethodSpec.Builder forMethod = MethodSpec.methodBuilder(forName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(Context.class, context)
                .returns(Intent.class);
        // build START method
        MethodSpec.Builder startMethod = MethodSpec.methodBuilder(getMethodName(METHOD_NAME_START, activityClassName))
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(Context.class, context);

        forMethod.addStatement("$T $N = new $T($N, $T.class)", Intent.class, intent, Intent.class, context, activityClassName)
                .addStatement("$T $N = new $T()", BundleWrapper.class, extras, BundleWrapper.class);
        startMethod.addCode("$N.startActivity($N($N", context, forName, context);
        for (VariableElement extra : allExtras) {
            TypeName typeName = ClassName.get(extra.asType());
            String name = extra.getSimpleName().toString();
            forMethod.addParameter(typeName, name);
            forMethod.addStatement("$N.put($S, $N)", extras, aib.getExtraId(extra), name);

            startMethod.addParameter(typeName, name);
            startMethod.addCode(", $N", name);
        }
        forMethod.addStatement("$N.putExtras($N.getBundle())", intent, extras)
                .addStatement("return $N", intent);
        startMethod.addCode("));\n");
        // add methods
        getBuilder().addMethod(forMethod.build());
        getBuilder().addMethod(startMethod.build());
    }

    private String getMethodName(String format, ClassName activityName) {
        return String.format(format, activityName.simpleName());
    }

}
