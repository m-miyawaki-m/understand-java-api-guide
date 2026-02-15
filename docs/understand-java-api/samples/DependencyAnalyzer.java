import com.scitools.understand.*;
import java.io.*;
import java.util.*;

/**
 * 依存関係分析のサンプル。
 *
 * 使い方:
 *   java -cp "Understand.jar;." DependencyAnalyzer <UDBファイルパス> <コマンド> [出力パス]
 *
 * コマンド:
 *   file-deps   - ファイル間依存関係を表示
 *   class-deps  - クラス間依存関係を表示
 *   csv         - 依存関係をCSV出力（第3引数に出力パス）
 */
public class DependencyAnalyzer {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("使い方: java DependencyAnalyzer <UDBファイルパス> <コマンド> [出力パス]");
            System.exit(1);
        }

        Database db = null;
        try {
            db = Understand.open(args[0]);
            String command = args[1];
            String outputPath = args.length > 2 ? args[2] : null;

            switch (command) {
                case "file-deps":
                    showFileDependencies(db);
                    break;
                case "class-deps":
                    showClassDependencies(db);
                    break;
                case "csv":
                    exportDependenciesCsv(db, outputPath);
                    break;
                default:
                    System.err.println("不明なコマンド: " + command);
            }
        } finally {
            if (db != null) db.close();
        }
    }

    /** ファイル間依存関係を表示 */
    private static void showFileDependencies(Database db) {
        Entity[] files = db.ents("file ~unknown ~unresolved");
        System.out.println("=== ファイル間依存関係 ===");
        for (Entity file : files) {
            Map<Entity, Reference[]> deps = file.depends();
            if (!deps.isEmpty()) {
                System.out.println(file.name() + " が依存するファイル:");
                for (Map.Entry<Entity, Reference[]> entry : deps.entrySet()) {
                    System.out.printf("  → %s (%d箇所の参照)%n",
                        entry.getKey().name(), entry.getValue().length);
                }
            }
        }
    }

    /** クラス間依存関係を表示 */
    private static void showClassDependencies(Database db) {
        Entity[] classes = db.ents("class ~unknown ~unresolved");
        System.out.println("=== クラス間依存関係 ===");
        for (Entity cls : classes) {
            Map<Entity, Reference[]> deps = cls.depends();
            if (!deps.isEmpty()) {
                System.out.println(cls.longname() + " が依存するクラス:");
                for (Map.Entry<Entity, Reference[]> entry : deps.entrySet()) {
                    Entity depTarget = entry.getKey();
                    Reference[] refs = entry.getValue();
                    System.out.printf("  → %s (%d箇所)%n", depTarget.longname(), refs.length);
                    // 参照の詳細を表示（先頭3件）
                    for (int i = 0; i < Math.min(3, refs.length); i++) {
                        Reference ref = refs[i];
                        System.out.printf("      %s 行%d (種別: %s)%n",
                            ref.file().name(), ref.line(), ref.kind().name());
                    }
                    if (refs.length > 3) {
                        System.out.printf("      ... 他 %d 件%n", refs.length - 3);
                    }
                }
            }

            // 逆方向: このクラスに依存しているクラス
            Map<Entity, Reference[]> depsBy = cls.dependsby();
            if (!depsBy.isEmpty()) {
                System.out.println(cls.longname() + " に依存するクラス:");
                for (Map.Entry<Entity, Reference[]> entry : depsBy.entrySet()) {
                    System.out.printf("  ← %s (%d箇所)%n",
                        entry.getKey().longname(), entry.getValue().length);
                }
            }
            System.out.println();
        }
    }

    /** 依存関係をCSVファイルに出力（Java標準ライブラリのみ使用） */
    private static void exportDependenciesCsv(Database db, String outputPath) throws IOException {
        if (outputPath == null) {
            System.err.println("出力パスを指定してください");
            return;
        }
        Entity[] classes = db.ents("class ~unknown ~unresolved");
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("依存元,依存先,参照数");
            for (Entity cls : classes) {
                Map<Entity, Reference[]> deps = cls.depends();
                for (Map.Entry<Entity, Reference[]> entry : deps.entrySet()) {
                    writer.printf("%s,%s,%d%n",
                        cls.longname(), entry.getKey().longname(), entry.getValue().length);
                }
            }
        }
        System.out.println("CSVを出力しました: " + outputPath);
    }
}
