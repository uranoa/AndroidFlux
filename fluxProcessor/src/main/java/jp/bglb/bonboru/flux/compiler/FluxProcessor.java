package jp.bglb.bonboru.flux.compiler;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;

import jp.bglb.bonboru.flux.compiler.annotation.ObservableClass;
import jp.bglb.bonboru.flux.compiler.annotation.ObservableField;
import jp.bglb.bonboru.flux.store.StoreChangeListener;
import rx.Observable;
import rx.subjects.BehaviorSubject;

/**
 * AnnotationされているDataクラスからViewの制御を行うためのStateクラスを作る
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({
        "jp.bglb.bonboru.flux.compiler.annotation.ObservableClass",
        "jp.bglb.bonboru.flux.compiler.annotation.ObservableField"
})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class FluxProcessor extends AbstractProcessor {

    Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<Element> dataClasses = new ArrayList<>(roundEnv.getElementsAnnotatedWith(ObservableClass.class));
        for (Element element : dataClasses) {
            TypeSpec.Builder classBuilder = TypeSpec.classBuilder(element.getSimpleName().toString() + "State")
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

            TypeSpec.Builder actionDataBuilder = TypeSpec.classBuilder(element.getSimpleName().toString() + "ActionResult")
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

            MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC);

            List<String> fields = new ArrayList<>();
            for (Element member : element.getEnclosedElements()) {
                if (member.getAnnotation(ObservableField.class) != null) {
                    final String fieldName = member.getSimpleName().toString();
                    ClassName behavior = ClassName.get(BehaviorSubject.class);
                    TypeMirror typeMirror = getType(member);
                    ClassName type = ClassName.bestGuess(typeMirror.toString());
                    TypeName behaviorOfType = ParameterizedTypeName.get(behavior, type);
                    try {
                        FieldSpec fieldSpec = FieldSpec.builder(behaviorOfType, fieldName)
                                .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
                                .build();
                        classBuilder.addField(fieldSpec);
                        constructorBuilder.addStatement("this.$N = $T.create()", fieldName, BehaviorSubject.class);
                        fields.add(fieldName);
                    } catch (MirroredTypeException t) {
                    }
                }
            }
            classBuilder.addMethod(constructorBuilder.build());

            TypeMirror dataType = getDataType(element);
            ClassName dataName = ClassName.bestGuess(dataType.toString());
            ClassName stateListener = ClassName.get(StoreChangeListener.class);
            classBuilder.addSuperinterface(ParameterizedTypeName.get(stateListener, dataName));
            classBuilder.addField(FieldSpec.builder(dataName, "state", Modifier.PRIVATE).build());

            // 自分の持ってるデータと渡されるデータの差分を検出して反映するメソッド
            MethodSpec.Builder onChangeBuilder = MethodSpec.methodBuilder("onChange")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(dataName, "data");
            for (String field : fields) {
                onChangeBuilder.beginControlFlow("if (this.$N.$N.equals($N.$N))", "state", field, "data", field)
                        .addStatement("this.$N.onNext($N.$N)", field, "data", field)
                        .endControlFlow();
            }
            onChangeBuilder.addStatement("this.$N = $N", "state", "data");
            classBuilder.addMethod(onChangeBuilder.build());

            try {
                System.out.println("package:" + element.getClass().getPackage().getName());
                JavaFile.builder(dataName.packageName(), classBuilder.build())
                        .build()
                        .writeTo(filer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    /**
     * FIXME: 例外に頼らない方法かつ、汎用的にしたい
     * ObservableFiledのAnnotationに設定されたclass情報を取得する
     * @param element annotated filed
     * @return typeMirror
     */
    private TypeMirror getType(Element element) {
        TypeMirror typeMirror = null;
        try {
            element.getAnnotation(ObservableField.class).value();
            throw new RuntimeException();
        } catch (Exception exception) {
            typeMirror = ((MirroredTypeException) exception).getTypeMirror();
        }
        return typeMirror;
    }

    /**
     * FIXME: 例外に頼らない方法かつ、汎用的にしたい
     * ObservableClassのAnnotationに設定されたclass情報を取得する
     * @param element annotated class
     * @return typeMirror
     */
    private TypeMirror getDataType(Element element) {
        TypeMirror typeMirror = null;
        try {
            element.getAnnotation(ObservableClass.class).value();
            throw new RuntimeException();
        } catch (Exception exception) {
            typeMirror = ((MirroredTypeException) exception).getTypeMirror();
        }
        return typeMirror;
    }

}