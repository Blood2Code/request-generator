package uz.umar;

import com.intellij.psi.*;
import java.util.Set;

/**
 * Unwraps common Spring/reactive/async wrappers around controller return types so the
 * real payload can drive response schemas and examples.
 *
 *   ResponseEntity<User>        → User
 *   Optional<User>              → User
 *   Mono<ResponseEntity<User>>  → User
 *   Flux<User> / Page<User>     → array of User (isArray() = true, element() = User)
 *   List<User> / User[]         → array of User
 */
public final class ResponseTypeResolver {

    private ResponseTypeResolver() {}

    // Single-value wrappers whose payload is their first type argument.
    private static final Set<String> TRANSPARENT = Set.of(
        "org.springframework.http.ResponseEntity",
        "org.springframework.http.HttpEntity",
        "java.util.Optional",
        "reactor.core.publisher.Mono",
        "java.util.concurrent.CompletableFuture",
        "java.util.concurrent.Future",
        "java.util.concurrent.Callable",
        "org.springframework.web.context.request.async.DeferredResult",
        "org.springframework.util.concurrent.ListenableFuture"
    );

    // Wrappers that represent a stream/collection of their first type argument.
    private static final Set<String> ARRAY_WRAPPERS = Set.of(
        "reactor.core.publisher.Flux",
        "org.springframework.data.domain.Page",
        "org.springframework.data.domain.Slice"
    );

    /** Strips transparent single-value wrappers (recursively). */
    public static PsiType unwrap(PsiType type) {
        if (type instanceof PsiClassType ct) {
            PsiClass cls = ct.resolve();
            String fqn = cls != null ? cls.getQualifiedName() : null;
            PsiType[] args = ct.getParameters();
            if (fqn != null && TRANSPARENT.contains(fqn) && args.length == 1) {
                return unwrap(args[0]);
            }
        }
        return type;
    }

    /** True when the (unwrapped) type is an array, a java collection, or a Flux/Page. */
    public static boolean isArray(PsiType type) {
        PsiType t = unwrap(type);
        if (t instanceof PsiArrayType) return true;
        if (t instanceof PsiClassType ct) {
            PsiClass cls = ct.resolve();
            String fqn = cls != null ? cls.getQualifiedName() : null;
            if (fqn == null) return false;
            return ARRAY_WRAPPERS.contains(fqn)
                || fqn.startsWith("java.util.List") || fqn.startsWith("java.util.Set")
                || fqn.startsWith("java.util.Collection") || fqn.startsWith("java.util.ArrayList")
                || fqn.startsWith("java.util.Iterable");
        }
        return false;
    }

    /** Element type of an array/collection response, or null if not an array or element unknown. */
    public static PsiType element(PsiType type) {
        PsiType t = unwrap(type);
        if (t instanceof PsiArrayType at) return at.getComponentType();
        if (t instanceof PsiClassType ct) {
            PsiType[] args = ct.getParameters();
            if (args.length >= 1) return args[0];
        }
        return null;
    }
}
