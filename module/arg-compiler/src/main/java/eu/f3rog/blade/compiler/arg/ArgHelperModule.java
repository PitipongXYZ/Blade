package eu.f3rog.blade.compiler.arg;

import android.os.Bundle;

import com.squareup.javapoet.MethodSpec;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import blade.Arg;
import eu.f3rog.blade.compiler.ErrorMsg;
import eu.f3rog.blade.compiler.builder.BaseClassBuilder;
import eu.f3rog.blade.compiler.builder.ClassManager;
import eu.f3rog.blade.compiler.builder.annotation.WeaveBuilder;
import eu.f3rog.blade.compiler.builder.helper.BaseHelperModule;
import eu.f3rog.blade.compiler.builder.helper.HelperClassBuilder;
import eu.f3rog.blade.compiler.module.BundleUtils;
import eu.f3rog.blade.compiler.util.ProcessorError;
import eu.f3rog.blade.core.BundleWrapper;

import static eu.f3rog.blade.compiler.util.ProcessorUtils.addClassAsParameter;
import static eu.f3rog.blade.compiler.util.ProcessorUtils.cannotHaveAnnotation;
import static eu.f3rog.blade.compiler.util.ProcessorUtils.fullName;
import static eu.f3rog.blade.compiler.util.ProcessorUtils.isFragmentSubClass;

/**
 * Class {@link ArgHelperModule}
 *
 * @author FrantisekGazo
 * @version 2015-12-15
 */
public class ArgHelperModule extends BaseHelperModule {

    private static final String METHOD_NAME_INJECT = "inject";

    private static final String ARG_ID_FORMAT = "<Arg-%s>";

    public static String getArgId(String arg) {
        return String.format(ARG_ID_FORMAT, arg);
    }

    private final List<String> mArgs = new ArrayList<>();

    @Override
    public void checkClass(TypeElement e) throws ProcessorError {
        if (!isFragmentSubClass(e)) {
            throw new ProcessorError(e, ArgErrorMsg.Invalid_class_with_Arg);
        }
    }

    @Override
    public void add(VariableElement e) throws ProcessorError {
        if (cannotHaveAnnotation(e)) {
            throw new ProcessorError(e, ErrorMsg.Invalid_field_with_annotation, Arg.class.getSimpleName());
        }

        mArgs.add(e.getSimpleName().toString());
    }

    @Override
    public boolean implement(HelperClassBuilder builder) throws ProcessorError {
        addMethodToFragmentFactory(builder);
        if (!mArgs.isEmpty()) {
            // add inject() only if there is something
            addInjectMethod(builder);
            return true;
        }
        return false;
    }

    private void addInjectMethod(BaseClassBuilder builder) {
        String target = "target";
        MethodSpec.Builder method = MethodSpec.methodBuilder(METHOD_NAME_INJECT)
                .addAnnotation(WeaveBuilder.weave().method("onCreate", Bundle.class)
                        .withStatement("%s.%s(this);", fullName(builder.getClassName()), METHOD_NAME_INJECT)
                        .build())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);

        addClassAsParameter(method, builder.getArgClassName(), target);

        method.beginControlFlow("if ($N.getArguments() == null)", target)
                .addStatement("return")
                .endControlFlow();

        String args = "args";
        method.addStatement("$T $N = $T.from($N.getArguments())", BundleWrapper.class, args, BundleWrapper.class, target);

        BundleUtils.getFromBundle(method, target, mArgs, ARG_ID_FORMAT, args);

        builder.getBuilder().addMethod(method.build());
    }

    private void addMethodToFragmentFactory(HelperClassBuilder builder) throws ProcessorError {
        ClassManager.getInstance()
                .getSpecialClass(FragmentFactoryBuilder.class)
                .addMethodFor(builder.getTypeElement());
    }

}
