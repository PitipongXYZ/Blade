package eu.f3rog.blade.compiler.new_approach.module.extra;

import android.content.Context;
import android.content.Intent;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import eu.f3rog.blade.compiler.name.GCN;
import eu.f3rog.blade.compiler.name.GPN;
import eu.f3rog.blade.compiler.new_approach.builder.BaseClassBuilder;
import eu.f3rog.blade.compiler.new_approach.builder.ClassManager;
import eu.f3rog.blade.compiler.new_approach.builder.helper.HelperClassBuilder;
import eu.f3rog.blade.compiler.util.ProcessorError;
import eu.f3rog.blade.core.BundleWrapper;

import static eu.f3rog.blade.compiler.util.ProcessorUtils.getSuperClass;

/**
 * Class {@link ActivityNavigatorBuilder}
 *
 * @author FrantisekGazo
 * @version 2015-10-21
 */
public class ActivityNavigatorBuilder extends BaseClassBuilder {

    private static final String METHOD_NAME_FOR = "for%s";
    private static final String METHOD_NAME_START = "start%s";

    public ActivityNavigatorBuilder() throws ProcessorError {
        super(GCN.NAVIGATOR, GPN.BLADE);
    }

    @Override
    public void start() throws ProcessorError {
        super.start();
        getBuilder().addModifiers(Modifier.FINAL, Modifier.PUBLIC);
    }

    public void addMethodsFor(TypeElement typeElement) throws ProcessorError {
        if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return;
        }

        TypeElement superClass = typeElement;
        List<VariableElement> extras = new ArrayList<>();

        while (superClass != null) {
            HelperClassBuilder helper = ClassManager.getInstance().getHelperIfExists(superClass);
            if (helper == null) break;

            ExtraHelperModule helperModule = helper.getModuleIfExists(ExtraHelperModule.class);
            if (helperModule == null) break;

            extras.addAll(0, helperModule.getExtras());

            superClass = getSuperClass(superClass);
        }

        integrate(ClassName.get(typeElement), extras);
    }

    private void integrate(ClassName activityClassName, List<VariableElement> allExtras) throws ProcessorError {
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
            forMethod.addStatement("$N.put($S, $N)", extras, ExtraHelperModule.getExtraId(extra), name);

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
