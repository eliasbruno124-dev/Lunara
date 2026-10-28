package de.lunara.tools;

import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedDeclaration;
import org.jetbrains.kotlin.psi.KtPsiFactory;

/** Syntax inventory only: no type resolution or runtime call-graph claims. */
public final class KotlinMap {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("root input-list output-json required");
        Path root = Path.of(args[0]).toRealPath();
        List<String> files = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8);
        Disposable disposable = Disposer.newDisposable();
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            var environment = KotlinCoreEnvironment.createForProduction(disposable,
                new CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES);
            var factory = new KtPsiFactory(environment.getProject(), false);
            for (String relative : files) {
                Path path = root.resolve(relative).normalize();
                if (!path.startsWith(root) || !path.toRealPath().startsWith(root))
                    throw new IllegalArgumentException("source outside root");
                String text = Files.readString(path, StandardCharsets.UTF_8);
                KtFile file = factory.createFile(path.getFileName().toString(), text);
                var errors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement.class);
                if (!errors.isEmpty()) throw new IllegalStateException("Kotlin syntax error in " + relative
                    + " at " + errors.iterator().next().getTextOffset());
                var declarations = new ArrayList<>(PsiTreeUtil.collectElementsOfType(file, KtNamedDeclaration.class));
                declarations.sort(Comparator.comparingInt(PsiElement::getTextOffset));
                List<Map<String, Object>> symbols = new ArrayList<>();
                for (var declaration : declarations) {
                    String name = declaration.getName();
                    if (name == null) continue;
                    var item = new LinkedHashMap<String, Object>();
                    item.put("name", name);
                    item.put("kind", declaration.getClass().getSimpleName().replaceFirst("^Kt", ""));
                    item.put("line", line(text, declaration.getTextOffset()));
                    List<String> owners = new ArrayList<>();
                    for (PsiElement parent = declaration.getParent(); parent != null; parent = parent.getParent()) {
                        if (parent instanceof KtNamedDeclaration named && named.getName() != null)
                            owners.add(0, named.getName());
                    }
                    item.put("owner", String.join(".", owners));
                    symbols.add(item);
                }
                result.put(relative, symbols);
            }
            Files.writeString(Path.of(args[2]), new GsonBuilder().disableHtmlEscaping().create().toJson(result), StandardCharsets.UTF_8);
        } finally {
            Disposer.dispose(disposable);
        }
    }

    private static int line(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) if (text.charAt(i) == '\n') line++;
        return line;
    }
}
