package eu.f3rog.blade.compiler.module.extra;

import android.app.Activity;
import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import blade.Extra;
import eu.f3rog.blade.compiler.ErrorMsg;
import eu.f3rog.blade.compiler.builder.ClassManager;
import eu.f3rog.blade.compiler.builder.helper.BaseHelperModule;
import eu.f3rog.blade.compiler.builder.helper.HelperClassBuilder;
import eu.f3rog.blade.compiler.builder.weaving.WeaveBuilder;
import eu.f3rog.blade.compiler.module.BundleUtils;
import eu.f3rog.blade.compiler.name.EClass;
import eu.f3rog.blade.compiler.util.ProcessorError;
import eu.f3rog.blade.compiler.util.ProcessorUtils;
import eu.f3rog.blade.core.BundleWrapper;

import static eu.f3rog.blade.compiler.util.ProcessorUtils.cannotHaveAnnotation;
import static eu.f3rog.blade.compiler.util.ProcessorUtils.fullName;

/**
 * Class {@link ExtraHelperModule}
 *
 * @author FrantisekGazo
 * @version 2015-12-15
 */
public class ExtraHelperModule extends BaseHelperModule {

    private enum Injected {
        ACTIVITY, SERVICE, INTENT_SERVICE
    }

    private static final String METHOD_NAME_INJECT = "inject";

    private static final String EXTRA_ID_FORMAT = "<Extra-%s>";

    public static String getExtraId(String extra) {
        return String.format(EXTRA_ID_FORMAT, extra);
    }

    private List<String> mExtras = new ArrayList<>();
    private Injected mInjected;

    @Override
    public void checkClass(TypeElement e) throws ProcessorError {
        if (ProcessorUtils.isSubClassOf(e, EClass.AppCompatActivity.getName(), ClassName.get(Activity.class))) {
            mInjected = Injected.ACTIVITY;
        } else if (ProcessorUtils.isSubClassOf(e, IntentService.class)) {
            mInjected = Injected.INTENT_SERVICE;
        } else if (ProcessorUtils.isSubClassOf(e, Service.class)) {
            mInjected = Injected.SERVICE;
        } else {
            throw new ProcessorError(e, ErrorMsg.Invalid_class_with_Extra);
        }
    }

    @Override
    public void add(VariableElement e) throws ProcessorError {
        if (cannotHaveAnnotation(e)) {
            throw new ProcessorError(e, ErrorMsg.Invalid_field_with_annotation, Extra.class.getSimpleName());
        }

        mExtras.add(e.getSimpleName().toString());
    }

    @Override
    public void implement(ProcessingEnvironment processingEnvironment, HelperClassBuilder builder) throws ProcessorError {
        if (!mExtras.isEmpty()) {
            // add inject() only if there is something
            addInjectMethod(builder);
        }
        addMethodToIntentManager(processingEnvironment, builder);
    }

    private void addInjectMethod(HelperClassBuilder builder) {
        String target = "target";
        String intent = "intent";
        MethodSpec.Builder method = MethodSpec.methodBuilder(METHOD_NAME_INJECT)
                .addAnnotation(weaveAnnotation(builder))
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(builder.getArgClassName(), target);

        if (mInjected == Injected.ACTIVITY) {
            method.addStatement("$T $N = $N.getIntent()", Intent.class, intent, target);
        } else {
            method.addParameter(Intent.class, intent);
        }

        method.beginControlFlow("if ($N == null || $N.getExtras() == null)", intent, intent)
                .addStatement("return")
                .endControlFlow();

        String extras = "extras";
        method.addStatement("$T $N = $T.from($N.getExtras())", BundleWrapper.class, extras, BundleWrapper.class, intent);

        BundleUtils.getFromBundle(method, target, mExtras, EXTRA_ID_FORMAT, extras);

        builder.getBuilder().addMethod(method.build());
    }

    private AnnotationSpec weaveAnnotation(HelperClassBuilder builder) {
        switch (mInjected) {
            case ACTIVITY:
                return WeaveBuilder.into("onCreate", Bundle.class)
                        .addStatement("%s.%s(this);", fullName(builder.getClassName()), METHOD_NAME_INJECT)
                        .build();
            case SERVICE:
                return WeaveBuilder.into("onStartCommand", Intent.class, int.class, int.class)
                        .addStatement("%s.%s(this, $1);", fullName(builder.getClassName()), METHOD_NAME_INJECT)
                        .build();
            case INTENT_SERVICE:
                return WeaveBuilder.into("onHandleIntent", Intent.class)
                        .addStatement("%s.%s(this, $1);", fullName(builder.getClassName()), METHOD_NAME_INJECT)
                        .build();
            default:
                throw new IllegalStateException();
        }
    }

    private void addMethodToIntentManager(ProcessingEnvironment processingEnvironment, HelperClassBuilder builder) throws ProcessorError {
        ClassManager.getInstance()
                .getSpecialClass(IntentBuilderBuilder.class)
                .addMethodsFor(processingEnvironment, builder.getTypeElement());
    }

}
