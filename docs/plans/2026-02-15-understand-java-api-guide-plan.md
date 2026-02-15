# Understand Java API 日本語ガイド 実装計画

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** SciTools Understand Java API の日本語ドキュメント（チュートリアル、ユースケースガイド、APIリファレンス、サンプルコード）を作成する

**Architecture:** claude-project-sample をコピーして新プロジェクト `understand-java-api-guide` を作成し、`docs/understand-java-api/` 配下にMarkdownドキュメント5本とJavaサンプルコード4本を段階的に配置する

**Tech Stack:** Markdown, Java (サンプルコード), SciTools Understand Java API (`com.scitools.understand`)

**Design doc:** `docs/plans/2026-02-15-understand-java-api-guide-design.md`

**参考資料パス:**
- Java API Javadoc: `/mnt/c/Program Files/SciTools/doc/manuals/java/com/scitools/understand/`
- Understand.jar: `/mnt/c/Program Files/SciTools/bin/pc-win64/Java/Understand.jar`

---

### Task 1: プロジェクト作成とディレクトリ構造セットアップ

**Files:**
- Create: `/home/miyaw/dev/understand-java-api-guide/` (プロジェクトルート)
- Create: `/home/miyaw/dev/understand-java-api-guide/docs/understand-java-api/samples/` (ディレクトリ)

**Step 1: claude-project-sample をコピー**

```bash
cp -r /home/miyaw/dev/claude-project-sample /home/miyaw/dev/understand-java-api-guide
```

**Step 2: 不要ファイルを整理**

コピー先の `.git` を削除して新規リポジトリとして初期化する。既存の `docs/plans/` は設計ドキュメントのみ残す。

```bash
cd /home/miyaw/dev/understand-java-api-guide
rm -rf .git
git init
```

不要なテンプレートファイル等を削除:
```bash
rm -rf templates/
rm -f setup.sh test-setup.sh
rm -f docs/browser-settings-guide.md
rm -f docs/plans/2026-01-28-*.md
```

**Step 3: ドキュメントディレクトリを作成**

```bash
mkdir -p /home/miyaw/dev/understand-java-api-guide/docs/understand-java-api/samples
```

**Step 4: README.md を更新**

`README.md` を以下の内容に書き換える:

```markdown
# Understand Java API 日本語ガイド

SciTools Understand の Java API (`com.scitools.understand`) の日本語ドキュメントです。

## ドキュメント構成

| ドキュメント | 内容 |
|-------------|------|
| [01-getting-started.md](docs/understand-java-api/01-getting-started.md) | 環境構築・プロジェクト組み込み |
| [02-core-concepts.md](docs/understand-java-api/02-core-concepts.md) | 主要概念の解説 |
| [03-code-exploration.md](docs/understand-java-api/03-code-exploration.md) | コード構造探索ガイド |
| [04-dependency-analysis.md](docs/understand-java-api/04-dependency-analysis.md) | 依存関係分析ガイド |
| [05-api-reference.md](docs/understand-java-api/05-api-reference.md) | 全クラス・メソッド日本語リファレンス |

## サンプルコード

`docs/understand-java-api/samples/` に実行可能なサンプルコードを配置しています。

## 前提条件

- SciTools Understand インストール済み
- Java 11 以上
- Understand データベース（`.udb`）作成済み
```

**Step 5: 設計ドキュメントをコピー**

```bash
cp /home/miyaw/dev/claude-project-sample/docs/plans/2026-02-15-understand-java-api-guide-design.md \
   /home/miyaw/dev/understand-java-api-guide/docs/plans/
```

**Step 6: 初期コミット**

```bash
cd /home/miyaw/dev/understand-java-api-guide
git add -A
git commit -m "chore: initialize understand-java-api-guide project"
```

---

### Task 2: 分析対象サンプルコード (SampleProject.java) を作成

**Files:**
- Create: `docs/understand-java-api/samples/SampleProject.java`

**Step 1: SampleProject.java を作成**

Understand で分析する対象となるJavaコード。以下の要素を含む:
- `Printable` インターフェース（1つ）
- `BaseItem` 抽象クラス（1つ、`Printable` を実装）
- `Task` 派生クラス（`BaseItem` を継承、フィールド参照・メソッド呼び出しあり）
- `TaskManager` クラス（`Task` を生成・管理、依存関係の実例）

```java
package sample;

/**
 * Understand API の動作確認用サンプルプロジェクト。
 * このファイルを Understand で解析し、APIから情報を取得するデモに使用する。
 */

// --- インターフェース ---
interface Printable {
    String toDisplayString();
}

// --- 基底クラス ---
abstract class BaseItem implements Printable {
    private final int id;
    private final String name;

    public BaseItem(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toDisplayString() {
        return String.format("[%d] %s", id, name);
    }

    public abstract boolean isValid();
}

// --- 派生クラス: Task ---
class Task extends BaseItem {
    public enum Priority { LOW, MEDIUM, HIGH }

    private Priority priority;
    private boolean completed;

    public Task(int id, String name, Priority priority) {
        super(id, name);
        this.priority = priority;
        this.completed = false;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void complete() {
        this.completed = true;
    }

    @Override
    public boolean isValid() {
        return getName() != null && !getName().isEmpty();
    }

    @Override
    public String toDisplayString() {
        return super.toDisplayString() + " [" + priority + "]"
            + (completed ? " (完了)" : " (未完了)");
    }
}

// --- TaskManager: Task を管理するクラス ---
class TaskManager {
    private final java.util.List<Task> tasks = new java.util.ArrayList<>();

    public void addTask(Task task) {
        if (task.isValid()) {
            tasks.add(task);
        }
    }

    public Task findById(int id) {
        for (Task task : tasks) {
            if (task.getId() == id) {
                return task;
            }
        }
        return null;
    }

    public java.util.List<Task> getByPriority(Task.Priority priority) {
        java.util.List<Task> result = new java.util.ArrayList<>();
        for (Task task : tasks) {
            if (task.getPriority() == priority) {
                result.add(task);
            }
        }
        return result;
    }

    public void completeTask(int id) {
        Task task = findById(id);
        if (task != null) {
            task.complete();
        }
    }

    public void printAll() {
        for (Task task : tasks) {
            System.out.println(task.toDisplayString());
        }
    }

    public int countCompleted() {
        int count = 0;
        for (Task task : tasks) {
            if (task.isCompleted()) {
                count++;
            }
        }
        return count;
    }
}

// --- メインクラス ---
public class SampleProject {
    public static void main(String[] args) {
        TaskManager manager = new TaskManager();

        manager.addTask(new Task(1, "設計書作成", Task.Priority.HIGH));
        manager.addTask(new Task(2, "コードレビュー", Task.Priority.MEDIUM));
        manager.addTask(new Task(3, "テスト実行", Task.Priority.LOW));

        manager.completeTask(1);
        manager.printAll();

        System.out.println("完了数: " + manager.countCompleted());
    }
}
```

**Step 2: コミット**

```bash
git add docs/understand-java-api/samples/SampleProject.java
git commit -m "feat: add sample Java project for Understand analysis target"
```

---

### Task 3: BasicUsage.java と 01-getting-started.md を作成

**Files:**
- Create: `docs/understand-java-api/samples/BasicUsage.java`
- Create: `docs/understand-java-api/01-getting-started.md`

**Step 1: BasicUsage.java を作成**

```java
import com.scitools.understand.*;

/**
 * Understand Java API の基本的な使い方を示すサンプル。
 *
 * 使い方:
 *   java -cp "Understand.jar;." BasicUsage <UDBファイルパス>
 */
public class BasicUsage {

    public static void main(String[] args) {
        // 引数チェック
        if (args.length < 1) {
            System.err.println("使い方: java BasicUsage <UDBファイルパス>");
            System.exit(1);
        }

        String dbPath = args[0];
        Database db = null;

        try {
            // データベースを開く
            db = Understand.open(dbPath);
            System.out.println("データベースを開きました: " + db.name());

            // 対応言語を表示
            String[] languages = db.language();
            System.out.println("言語: " + String.join(", ", languages));

            // 全エンティティを取得して表示
            Entity[] allEntities = db.ents(null);
            System.out.println("エンティティ総数: " + allEntities.length);

            // クラスエンティティのみ取得
            Entity[] classes = db.ents("class");
            System.out.println("\n--- クラス一覧 ---");
            for (Entity cls : classes) {
                System.out.printf("  %s (種別: %s)%n", cls.longname(), cls.kind().name());
            }

            // メソッドエンティティのみ取得
            Entity[] methods = db.ents("method");
            System.out.println("\n--- メソッド一覧 ---");
            for (Entity method : methods) {
                System.out.printf("  %s (種別: %s)%n", method.longname(), method.kind().name());
            }

        } catch (UnderstandException e) {
            System.err.println("エラー: " + e.getMessage());
            System.exit(1);
        } finally {
            // 必ずデータベースを閉じる
            if (db != null) {
                db.close();
                System.out.println("\nデータベースを閉じました。");
            }
        }
    }
}
```

**Step 2: 01-getting-started.md を作成**

Javadocの以下のファイルを参照しながら記述する:
- `/mnt/c/Program Files/SciTools/doc/manuals/java/com/scitools/understand/Understand.html` (`open()` のエラーコード一覧)
- `/mnt/c/Program Files/SciTools/doc/manuals/java/com/scitools/understand/Database.html` (`ents()`, `close()`)

内容:
- 前提条件
- Understand.jar の所在パス（Windows: `C:\Program Files\SciTools\bin\pc-win64\Java\Understand.jar`）
- Maven プロジェクトへの組み込み（`systemPath` によるローカルJAR依存追加）
- Gradle プロジェクトへの組み込み（`files()` によるローカルJAR依存追加）
- `java -cp` による直接実行方法
- UDB ファイルの作成手順（Understand GUI から / `und` コマンドから）
- BasicUsage.java のコード解説（抜粋引用）
- 実行例と期待出力
- よくあるエラーと対処表:
  - `DBAlreadyOpen`: 既に別のDBを開いている → 先に `close()` を呼ぶ
  - `DBCorrupt`: DB ファイルが破損 → 再作成
  - `DBOldVersion` / `DBUnknownVersion`: DB バージョン不一致 → 再ビルド
  - `DBUnableOpen`: ファイルが見つからない → パス確認
  - `NoApiLicense`: ライセンスが必要 → ライセンス設定確認
  - `UnsatisfiedLinkError`: ネイティブライブラリ未検出 → パス/環境変数確認

**Step 3: コミット**

```bash
git add docs/understand-java-api/samples/BasicUsage.java docs/understand-java-api/01-getting-started.md
git commit -m "docs: add getting started guide and basic usage sample"
```

---

### Task 4: 02-core-concepts.md を作成

**Files:**
- Create: `docs/understand-java-api/02-core-concepts.md`

**Step 1: 02-core-concepts.md を作成**

Javadocの全クラスの説明を参照しながら記述する:
- `/mnt/c/Program Files/SciTools/doc/manuals/java/com/scitools/understand/*.html`

内容:

1. **オブジェクトモデル概念図** (テキストベースの図)
```
Understand.open(path)
    └── Database
         ├── ents(kindstring) → Entity[]
         ├── lookup_uniquename(name) → Entity
         └── close()

Entity
    ├── kind() → Kind
    ├── refs(refkind, entkind, unique) → Reference[]
    ├── ents(refkind, entkind) → Entity[]
    ├── depends() → Map<Entity, Reference[]>
    ├── dependsby() → Map<Entity, Reference[]>
    └── lexer(lookupEnts, showInactive, expandMacros) → Lexer
         └── first() → Lexeme → next() → Lexeme → ...

Reference
    ├── ent() → Entity (参照先)
    ├── scope() → Entity (参照元)
    ├── file() → Entity (ファイル)
    ├── kind() → Kind
    ├── line() → int
    └── column() → int
```

2. **Entity（エンティティ）の解説**: ソースコード上の名前付き要素。`name()`, `longname()`, `uniquename()`, `kind()`, `type()` の違いを表で整理

3. **Reference（参照）の解説**: エンティティ間の関係。`ent()`, `scope()`, `file()` の関係を図示。参照の方向性（「AがBを呼ぶ」→ scope=A, ent=B）

4. **Kind（種別）の解説**: エンティティと参照の分類。kindstringフィルタの書き方:
   - 単一指定: `"class"`, `"method"`
   - 複数指定: `"class, interface"`
   - 否定: `"~unknown"`
   - Java向け代表的なkindstring一覧表

5. **Lexer/Lexeme（字句解析）の解説**: ファイルのトークン解析。`first()` → `next()` の双方向リンクリスト。`token()` の種類（`"keyword"`, `"identifier"`, `"string"` 等）

6. **リソース管理の注意点**: `Database.close()` の必須呼び出し、try-finally パターン、1DB制約

**Step 2: コミット**

```bash
git add docs/understand-java-api/02-core-concepts.md
git commit -m "docs: add core concepts guide for Understand Java API"
```

---

### Task 5: CodeExplorer.java と 03-code-exploration.md を作成

**Files:**
- Create: `docs/understand-java-api/samples/CodeExplorer.java`
- Create: `docs/understand-java-api/03-code-exploration.md`

**Step 1: CodeExplorer.java を作成**

```java
import com.scitools.understand.*;

/**
 * コード構造探索のサンプル。
 *
 * 使い方:
 *   java -cp "Understand.jar;." CodeExplorer <UDBファイルパス> <コマンド>
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
                // このクラスが定義しているメソッドを取得
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
                // このメソッドが呼び出しているメソッド
                Reference[] callRefs = method.refs("call", "method", true);
                System.out.println("=== " + method.longname() + " が呼び出すメソッド ===");
                for (Reference ref : callRefs) {
                    System.out.printf("  → %s (行: %d)%n", ref.ent().longname(), ref.line());
                }

                // このメソッドを呼び出しているメソッド
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
                    // 空白は読みやすさのためスキップ
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
```

**Step 2: 03-code-exploration.md を作成**

内容:
- 導入: コード構造探索とは何か、どのような場面で役立つか
- ユースケース1: 全クラス一覧の取得 (`db.ents("class ~unknown ~unresolved")`)
  - kindstring のフィルタリング解説（`~unknown` で未解決を除外）
  - コード抜粋 + 実行例 + 出力例
- ユースケース2: 特定クラスのメソッド一覧 (`cls.refs("define", "method", true)`)
  - `refs()` の3引数（refkindstring, entkindstring, unique）の使い分け
  - コード抜粋 + 実行例 + 出力例
- ユースケース3: メソッド呼び出し関係 (`method.refs("call", ...) / method.refs("callby", ...)`)
  - 参照の方向性（call vs callby）の図解
  - コード抜粋 + 実行例 + 出力例
- ユースケース4: 変数の参照箇所 (`ent.refs(null, null, false)`)
  - Reference の各属性（scope, ent, file, line, column, kind）の読み方
  - コード抜粋 + 実行例 + 出力例
- ユースケース5: 字句解析 (`file.lexer() → Lexeme`)
  - Lexer の生成オプション（lookupEnts, showInactive, expandMacros）
  - Lexeme の双方向リンクリスト走査
  - `token()` の代表的な値一覧
  - コード抜粋 + 実行例 + 出力例

**Step 3: コミット**

```bash
git add docs/understand-java-api/samples/CodeExplorer.java docs/understand-java-api/03-code-exploration.md
git commit -m "docs: add code exploration guide and CodeExplorer sample"
```

---

### Task 6: DependencyAnalyzer.java と 04-dependency-analysis.md を作成

**Files:**
- Create: `docs/understand-java-api/samples/DependencyAnalyzer.java`
- Create: `docs/understand-java-api/04-dependency-analysis.md`

**Step 1: DependencyAnalyzer.java を作成**

```java
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
 *   graph       - 依存関係グラフ画像を生成（第3引数に出力パス、第4引数にエンティティ名）
 */
public class DependencyAnalyzer {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("使い方: java DependencyAnalyzer <UDBファイルパス> <コマンド> [出力パス] [エンティティ名]");
            System.exit(1);
        }

        Database db = null;
        try {
            db = Understand.open(args[0]);
            String command = args[1];
            String outputPath = args.length > 2 ? args[2] : null;
            String entityName = args.length > 3 ? args[3] : null;

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
                case "graph":
                    generateGraph(db, outputPath, entityName);
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

    /** 依存関係をCSVファイルに出力 */
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

    /** 依存関係グラフ画像を生成 */
    private static void generateGraph(Database db, String outputPath, String entityName)
            throws UnderstandException {
        if (outputPath == null || entityName == null) {
            System.err.println("出力パスとエンティティ名を指定してください");
            return;
        }
        Entity[] entities = db.ents("class ~unknown ~unresolved");
        for (Entity ent : entities) {
            if (ent.longname().equals(entityName) || ent.name().equals(entityName)) {
                // 依存関係グラフを生成
                ent.draw("DependsOn", outputPath, null);
                System.out.println("グラフを出力しました: " + outputPath);
                return;
            }
        }
        System.out.println("エンティティが見つかりません: " + entityName);
    }
}
```

**Step 2: 04-dependency-analysis.md を作成**

内容:
- 導入: 依存関係分析とは何か、なぜ重要か（循環依存の検出、影響範囲の把握等）
- ユースケース1: ファイル間依存関係 (`file.depends()`)
  - `depends()` の戻り値 `Map<Entity, Reference[]>` の構造解説
  - コード抜粋 + 実行例 + 出力例
- ユースケース2: クラス間依存関係 (`cls.depends()` / `cls.dependsby()`)
  - 順方向（depends）と逆方向（dependsby）の違い
  - Reference 配列から詳細情報を取得する方法
  - コード抜粋 + 実行例 + 出力例
- ユースケース3: CSV出力
  - `PrintWriter` を使ったCSVエクスポート
  - 出力フォーマットの解説
  - コード抜粋 + 実行例
- ユースケース4: グラフ画像生成 (`Entity.draw()`)
  - `draw()` の引数解説: graph名（"DependsOn", "Butterfly", "Called By" 等）
  - 出力形式（.jpg, .png, .svg, .vdx）
  - options パラメータ（"Layout=Crossing;name=Fullname" 等）
  - コード抜粋 + 実行例

**Step 3: コミット**

```bash
git add docs/understand-java-api/samples/DependencyAnalyzer.java docs/understand-java-api/04-dependency-analysis.md
git commit -m "docs: add dependency analysis guide and DependencyAnalyzer sample"
```

---

### Task 7: 05-api-reference.md を作成

**Files:**
- Create: `docs/understand-java-api/05-api-reference.md`

**Step 1: 05-api-reference.md を作成**

Javadocの全HTMLファイルを参照して各メソッドの日本語リファレンスを作成する:
- `/mnt/c/Program Files/SciTools/doc/manuals/java/com/scitools/understand/*.html`

構成:

1. **Understand クラス** (3メソッド)
   - `static Database open(String name)` - DB接続
   - `static String[] metriclist(String kindstring)` - メトリクス名一覧
   - `static void loadNativeLibrary()` - ネイティブライブラリ読み込み

2. **Database クラス** (10メソッド)
   - `String name()`, `void close()`, `Entity[] ents(String kinds)`
   - `Entity lookup_uniquename(String uniquename)`
   - `String[] language()`, `String[] metrics()`
   - `Number metric(String name)`, `Map<String,Number> metric(String[] names)`
   - `byte[] metrics_treemap(String sizemetric, String colormetric, String enttype)`
   - `Database comparison_db()`

3. **Entity クラス** (23メソッド)
   - 基本情報: `id()`, `name()`, `uniquename()`, `longname()`, `kind()`, `type()`, `language()`, `library()`, `value()`, `parameters()`, `contents()`, `parent()`
   - フリーテキスト/コメント: `freetext(String kind)`, `comments(boolean after, boolean raw, String kinds)`
   - 参照: `refs(...)`, `filerefs(...)`, `ents(...)`
   - メトリクス: `metrics()`, `metric(String)`, `metric(String[])`
   - 描画: `draw(String graph, String filename, String options)`
   - Info Browser: `ib(String options)`
   - 字句解析: `lexer(boolean, boolean, boolean)`
   - 依存関係: `depends()`, `dependsby()`

4. **Reference クラス** (7メソッド)
   - `int line()`, `int column()`, `Entity ent()`, `Entity file()`, `Entity scope()`, `Kind kind()`, `boolean isforward()`

5. **Kind クラス** (3メソッド)
   - `String name()`, `boolean check(String kindstring)`, `String toString()`

6. **Lexer クラス** (4メソッド)
   - `Lexeme first()`, `Lexeme lexeme(int line, int column)`, `Lexeme[] lexemes(int startLine, int endLine)`, `int lines()`

7. **Lexeme クラス** (11メソッド)
   - `String text()`, `String token()`, `Lexeme next()`, `Lexeme previous()`
   - `Entity entity()`, `Reference reference()`
   - `int lineBegin()`, `int lineEnd()`, `int columnBegin()`, `int columnEnd()`
   - `boolean inactive()`

8. **UnderstandException クラス** - 4コンストラクタ

9. **付録: Java向け kindstring フィルタ一覧表**
   - エンティティ種別: class, interface, enum, method, variable, parameter, file, package 等
   - 参照種別: call, callby, use, useby, define, definein, set, setby, implement, implementby, extend, extendby 等
   - 修飾子: public, private, protected, static, abstract, final 等
   - 除外フィルタ: ~unknown, ~unresolved

各メソッドのフォーマット:

```markdown
#### `メソッドシグネチャ`

説明文（日本語）

| 項目 | 内容 |
|------|------|
| 引数 | `引数名` - 説明 |
| 戻り値 | 型 - 説明 |
| 例外 | 条件 - 説明 |

**使用例:**
\```java
// 1-3行の簡潔な例
\```
```

**Step 2: コミット**

```bash
git add docs/understand-java-api/05-api-reference.md
git commit -m "docs: add complete Japanese API reference for Understand Java API"
```

---

### Task 8: 最終確認とリンク整合性チェック

**Step 1: 全ファイルの存在確認**

```bash
ls -la docs/understand-java-api/
ls -la docs/understand-java-api/samples/
```

期待されるファイル:
- `01-getting-started.md`
- `02-core-concepts.md`
- `03-code-exploration.md`
- `04-dependency-analysis.md`
- `05-api-reference.md`
- `samples/SampleProject.java`
- `samples/BasicUsage.java`
- `samples/CodeExplorer.java`
- `samples/DependencyAnalyzer.java`

**Step 2: ドキュメント内のリンク・参照整合性チェック**

各ドキュメント内のサンプルコード参照パスが正しいか確認。README.md のリンクが全ドキュメントを指しているか確認。

**Step 3: 最終コミット（必要な修正がある場合）**

```bash
git add -A
git commit -m "docs: fix any link or reference issues"
```
