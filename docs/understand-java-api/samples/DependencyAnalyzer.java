import com.scitools.understand.*;
import java.io.*;
import java.util.*;

/**
 * 依存関係分析のサンプル。
 *
 * 使い方:
 *   java -cp "Understand.jar;." DependencyAnalyzer <UDBファイルパス> <コマンド> [引数]
 *
 * コマンド:
 *   file-deps   - ファイル間依存関係を表示
 *   class-deps  - クラス間依存関係を表示
 *   csv-all     - コード構造情報をCSV一括出力（第3引数に出力ディレクトリ）
 */
public class DependencyAnalyzer {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("使い方: java DependencyAnalyzer <UDBファイルパス> <コマンド> [引数]");
            System.exit(1);
        }

        Database db = null;
        try {
            db = Understand.open(args[0]);
            String command = args[1];

            switch (command) {
                case "file-deps":
                    showFileDependencies(db);
                    break;
                case "class-deps":
                    showClassDependencies(db);
                    break;
                case "csv-all":
                    String outputDir = args.length > 2 ? args[2] : null;
                    exportAllCsv(db, outputDir);
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

    /** コード構造情報をCSVファイルに一括出力 */
    private static void exportAllCsv(Database db, String outputDir) throws IOException {
        if (outputDir == null) {
            System.err.println("出力ディレクトリを指定してください");
            return;
        }
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Entity[] classes = db.ents("class ~unknown ~unresolved");

        exportClassesCsv(classes, dir);
        exportMethodsCsv(classes, dir);
        exportCallsCsv(classes, dir);
        exportCalledByCsv(classes, dir);

        System.out.println("CSVを出力しました: " + dir.getAbsolutePath());
        System.out.println("  - classes.csv（クラス一覧）");
        System.out.println("  - methods.csv（関数定義一覧）");
        System.out.println("  - calls.csv（関数呼び出し一覧）");
        System.out.println("  - calledby.csv（関数の被呼び出し一覧）");
    }

    /** クラス一覧をCSV出力 */
    private static void exportClassesCsv(Entity[] classes, File dir) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(new File(dir, "classes.csv")))) {
            w.println("クラス名,種別,ファイル名,定義行");
            for (Entity cls : classes) {
                // クラス定義の参照を取得
                Reference[] defRefs = cls.refs("definein", null, true);
                String fileName = "";
                int line = 0;
                if (defRefs.length > 0) {
                    fileName = defRefs[0].file().name();
                    line = defRefs[0].line();
                }
                w.printf("%s,%s,%s,%d%n",
                    cls.longname(), cls.kind().name(), fileName, line);
            }
        }
    }

    /** メソッド定義一覧をCSV出力 */
    private static void exportMethodsCsv(Entity[] classes, File dir) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(new File(dir, "methods.csv")))) {
            w.println("クラス名,メソッド名,戻り値型,ファイル名,定義行");
            for (Entity cls : classes) {
                // クラスが定義しているメソッドを取得
                Reference[] methodRefs = cls.refs("define", "method", true);
                for (Reference ref : methodRefs) {
                    Entity method = ref.ent();
                    w.printf("%s,%s,%s,%s,%d%n",
                        cls.longname(), method.name(), method.type(),
                        ref.file().name(), ref.line());
                }
            }
        }
    }

    /** 関数呼び出し一覧をCSV出力 */
    private static void exportCallsCsv(Entity[] classes, File dir) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(new File(dir, "calls.csv")))) {
            w.println("呼び出し元クラス,呼び出し元メソッド,呼び出し先クラス,呼び出し先メソッド,ファイル名,呼び出し行");
            for (Entity cls : classes) {
                Reference[] methodRefs = cls.refs("define", "method", true);
                for (Reference methodRef : methodRefs) {
                    Entity method = methodRef.ent();
                    // このメソッドが呼び出している他のメソッド
                    Reference[] callRefs = method.refs("call", "method", true);
                    for (Reference callRef : callRefs) {
                        Entity calledMethod = callRef.ent();
                        // 呼び出し先メソッドの所属クラスを取得
                        String calledClass = getOwnerClassName(calledMethod);
                        w.printf("%s,%s,%s,%s,%s,%d%n",
                            cls.longname(), method.name(),
                            calledClass, calledMethod.name(),
                            callRef.file().name(), callRef.line());
                    }
                }
            }
        }
    }

    /** 関数の被呼び出し一覧をCSV出力 */
    private static void exportCalledByCsv(Entity[] classes, File dir) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(new File(dir, "calledby.csv")))) {
            w.println("対象クラス,対象メソッド,呼び出し元クラス,呼び出し元メソッド,ファイル名,呼び出し行");
            for (Entity cls : classes) {
                Reference[] methodRefs = cls.refs("define", "method", true);
                for (Reference methodRef : methodRefs) {
                    Entity method = methodRef.ent();
                    // このメソッドを呼び出しているメソッド
                    Reference[] callByRefs = method.refs("callby", "method", true);
                    for (Reference callByRef : callByRefs) {
                        Entity callerMethod = callByRef.ent();
                        String callerClass = getOwnerClassName(callerMethod);
                        w.printf("%s,%s,%s,%s,%s,%d%n",
                            cls.longname(), method.name(),
                            callerClass, callerMethod.name(),
                            callByRef.file().name(), callByRef.line());
                    }
                }
            }
        }
    }

    /** メソッドの所属クラス名を取得するヘルパー */
    private static String getOwnerClassName(Entity method) {
        // definein 参照でメソッドを定義しているクラスを逆引き
        Reference[] defInRefs = method.refs("definein", "class", true);
        if (defInRefs.length > 0) {
            return defInRefs[0].ent().longname();
        }
        return "";
    }
}
