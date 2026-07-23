package uz.umar;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jetbrains.uast.UastContextKt.toUElement;

/**
 * Extracts top-level PSI classes from a source file, working for both Java and Kotlin.
 * Kotlin classes are reached through UAST, which exposes them as Java light classes so
 * the rest of the Java-PSI-based pipeline needs no changes.
 */
public final class ControllerClasses {

    private ControllerClasses() {}

    public static List<PsiClass> from(PsiFile file) {
        if (file instanceof PsiJavaFile javaFile) {
            return Arrays.asList(javaFile.getClasses());
        }
        UFile uFile = toUElement(file, UFile.class);
        if (uFile == null) return List.of();
        List<PsiClass> result = new ArrayList<>();
        for (UClass uClass : uFile.getClasses()) {
            PsiClass psi = uClass.getJavaPsi();
            if (psi != null) result.add(psi);
        }
        return result;
    }
}
