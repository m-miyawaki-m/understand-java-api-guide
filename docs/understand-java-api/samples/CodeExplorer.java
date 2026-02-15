import com.scitools.understand.*;

/**
 * コード構造探索のサンプル。
 *
 * 使い方:
 *   java -cp "Understand.jar;." CodeExplorer <UDBファイルパス> <コマンド> [対象名]
 *
 * コマンド:
 *   classes    - 全クラス一覧
 *   methods    - 指定クラスのメソッド一覧（第3引数にクラス名）
 *   calls      - メソッド呼び出し関係（第3引数にメソッド名）
 *   refs       - 変数の参照箇所（第3引数に変数名）
 *   lexer      - ファイルの字句解析（第3引数にファイルエンティティ名）
 */
public class CodeExplorer {

    public static void main(String[] args) throws UnderstandException {
        if (args.length < 2) {
            System.err.println("使い方: java CodeExplorer <UDBファイルパス> <コマンド> [対象名]");
            System.exit(1);
        }

        Database db = null;
        try {
            db = Understand.open(args[0]);
            String command = args[1];
            String target = args.length > 2 ? args[2] : null;

            switch (command) {
                case "classes":
                    listClasses(db);
                    break;
                case "methods":
                    listMethods(db, target);
                    break;
                case "calls":
                    showCallRelations(db, target);
                    break;
                case "refs":
                    showReferences(db, target);
                    break;
                case "lexer":
                    analyzeLexemes(db, target);
                    break;
                default:
                    System.err.println("不明なコマンド: " + command);
            }
        } finally {
            if (db != null) db.close();
        }
    }

    /** 全クラス一覧を表示 */
    private static void listClasses(Database db) {
        Entity[] classes = db.ents("class ~unknown ~unresolved");
        System.out.println("=== クラス一覧 ===");
        for (Entity cls : classes) {
            System.out.printf("  %s (種別: %s)%n", cls.longname(), cls.kind().name());
        }
        System.out.println("合計: " + classes.length + " クラス");
    }

    /** 指定クラスのメソッド一覧を表示 */
    private static void listMethods(Database db, String className) {
        if (className == null) {
            System.err.println("クラス名を指定してください");
            return;
        }
        Entity[] classes = db.ents("class");
        for (Entity cls : classes) {
            if (cls.name().equals(className)) {
                Reference[] methodRefs = cls.refs("define", "method", true);
                System.out.println("=== " + className + " のメソッド一覧 ===");
                for (Reference ref : methodRefs) {
                    Entity method = ref.ent();
                    System.out.printf("  %s (戻り値型: %s, 行: %d)%n",
                        method.name(), method.type(), ref.line());
                }
                return;
            }
        }
        System.out.println("クラスが見つかりません: " + className);
    }

    /** メソッドの呼び出し元・呼び出し先を表示 */
    private static void showCallRelations(Database db, String methodName) {
        if (methodName == null) {
            System.err.println("メソッド名を指定してください");
            return;
        }
        Entity[] methods = db.ents("method");
        for (Entity method : methods) {
            if (method.name().equals(methodName)) {
                Reference[] callRefs = method.refs("call", "method", true);
                System.out.println("=== " + method.longname() + " が呼び出すメソッド ===");
                for (Reference ref : callRefs) {
                    System.out.printf("  → %s (行: %d)%n", ref.ent().longname(), ref.line());
                }

                Reference[] calledByRefs = method.refs("callby", "method", true);
                System.out.println("=== " + method.longname() + " を呼び出すメソッド ===");
                for (Reference ref : calledByRefs) {
                    System.out.printf("  ← %s (行: %d)%n", ref.ent().longname(), ref.line());
                }
                return;
            }
        }
        System.out.println("メソッドが見つかりません: " + methodName);
    }

    /** 変数の参照箇所を表示 */
    private static void showReferences(Database db, String varName) {
        if (varName == null) {
            System.err.println("変数名を指定してください");
            return;
        }
        Entity[] entities = db.ents("variable");
        for (Entity ent : entities) {
            if (ent.name().equals(varName)) {
                Reference[] refs = ent.refs(null, null, false);
                System.out.println("=== " + ent.longname() + " の参照箇所 ===");
                for (Reference ref : refs) {
                    System.out.printf("  %s (参照種別: %s, ファイル: %s, 行: %d, 列: %d)%n",
                        ref.scope().longname(),
                        ref.kind().name(),
                        ref.file().name(),
                        ref.line(),
                        ref.column());
                }
                return;
            }
        }
        System.out.println("変数が見つかりません: " + varName);
    }

    /** ファイルの字句解析結果を表示 */
    private static void analyzeLexemes(Database db, String fileName) throws UnderstandException {
        if (fileName == null) {
            System.err.println("ファイル名を指定してください");
            return;
        }
        Entity[] files = db.ents("file");
        for (Entity file : files) {
            if (file.name().equals(fileName)) {
                Lexer lexer = file.lexer(true, false, false);
                System.out.println("=== " + fileName + " の字句解析 (先頭20トークン) ===");
                Lexeme lex = lexer.first();
                int count = 0;
                while (lex != null && count < 20) {
                    if (!"Whitespace".equals(lex.token()) && !"Newline".equals(lex.token())) {
                        Entity ent = lex.entity();
                        System.out.printf("  行%d 列%d: %-12s \"%s\"%s%n",
                            lex.lineBegin(),
                            lex.columnBegin(),
                            lex.token(),
                            lex.text(),
                            ent != null ? " → " + ent.longname() : "");
                        count++;
                    }
                    lex = lex.next();
                }
                return;
            }
        }
        System.out.println("ファイルが見つかりません: " + fileName);
    }
}
