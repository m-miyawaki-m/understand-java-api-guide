# 04 - 依存関係の分析（Dependency Analysis）

ソフトウェアの品質と保守性を左右する重要な要素の一つが、モジュール間の**依存関係**です。
Understand Java API を使えば、ファイル間やクラス間の依存関係をプログラム的に抽出・分析し、以下のような課題に対応できます。

- **循環依存の検出** — モジュール A が B に依存し、B が A に依存しているような循環構造を発見する
- **変更影響範囲の把握** — あるクラスを修正した際に影響を受ける他のクラスやファイルを特定する
- **アーキテクチャの健全性チェック** — レイヤー間の依存方向が設計方針に沿っているかを検証する

本章では、`DependencyAnalyzer.java`（[ソースコード全文](samples/DependencyAnalyzer.java)）を使い、依存関係の取得からCSVエクスポートまでを解説します。

## DependencyAnalyzer.java の概要

`DependencyAnalyzer.java` は、コマンドライン引数でデータベースファイルとコマンドを受け取り、3 種類の依存関係分析を実行できるサンプルプログラムです。

### 使い方

```
java -cp "Understand.jar;." DependencyAnalyzer <UDBファイルパス> <コマンド> [引数]
```

### コマンド一覧

| コマンド | 説明 | 追加引数 |
|---------|------|---------|
| `file-deps` | ファイル間依存関係を表示 | 不要 |
| `class-deps` | クラス間依存関係を表示 | 不要 |
| `csv-all` | コード構造情報をCSV一括出力 | 出力ディレクトリ |

以下の各ユースケースでは、`SampleProject.java`（[ソースコード全文](samples/SampleProject.java)）を Understand で解析して作成した `sample.udb` を対象としています。

---

## ユースケース1: ファイル間依存関係

最も基本的な依存関係分析は、ファイル単位の依存関係を調べることです。
あるファイルがどのファイルに依存しているかを把握することで、変更時の影響範囲をファイルレベルで見積もることができます。

### コード

```java
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
```

### 解説

`entity.depends()` は、対象エンティティが依存している他のエンティティを `Map<Entity, Reference[]>` 形式で返します。

この戻り値の構造は以下のとおりです。

| 要素 | 説明 |
|------|------|
| Key（`Entity`） | 依存先のエンティティ |
| Value（`Reference[]`） | その依存関係に寄与する参照の配列。依存先への参照が複数箇所にある場合、それぞれが配列の要素として含まれる |

たとえば、ファイル A がファイル B 内のクラスを 3 箇所で使用している場合、Map の Key がファイル B の Entity、Value が 3 つの Reference を含む配列になります。

```
depends() の戻り値:
Map<Entity, Reference[]>
  ┌─────────────────────┬─────────────────────────────────┐
  │ Key (依存先)         │ Value (参照の配列)               │
  ├─────────────────────┼─────────────────────────────────┤
  │ FileB の Entity      │ [ref1, ref2, ref3]              │
  │ FileC の Entity      │ [ref4]                          │
  └─────────────────────┴─────────────────────────────────┘
```

### 実行例

```bash
java -cp "Understand.jar;." DependencyAnalyzer sample.udb file-deps
```

### 出力例

```
=== ファイル間依存関係 ===
SampleProject.java が依存するファイル:
  → [Java] Object.class (3箇所の参照)
  → [Java] String.class (8箇所の参照)
  → [Java] List.class (4箇所の参照)
  → [Java] ArrayList.class (2箇所の参照)
  → [Java] PrintStream.class (3箇所の参照)
```

> **補足:** 単一ファイルのプロジェクト（`SampleProject.java`）の場合、ファイル間の依存は主に Java 標準ライブラリのクラスファイルへの参照として表示されます。
> 複数のソースファイルで構成されるプロジェクトでは、ソースファイル同士の依存関係がより明確に表れます。

---

## ユースケース2: クラス間依存関係

クラス単位の依存関係分析では、`depends()` と `dependsby()` を使って双方向の依存関係を調べます。
これにより、あるクラスの変更がどのクラスに影響するかを正確に把握できます。

### `depends()` と `dependsby()` の違い

| メソッド | 方向 | 意味 |
|---------|------|------|
| `depends()` | 順方向 | このクラスが**依存する先**を取得する |
| `dependsby()` | 逆方向 | このクラスに**依存しているもの**を取得する |

以下の図は、`TaskManager` クラスを中心とした双方向の依存関係を示しています。

```
depends() — 順方向（このクラスが依存する先）

                         depends()
    TaskManager ──────────────────→ Task
    TaskManager ──────────────────→ BaseItem


dependsby() — 逆方向（このクラスに依存しているもの）

                        dependsby()
    SampleProject ────────────────→ TaskManager
```

### Reference 配列から詳細情報を取得する

`depends()` が返す `Reference[]` には、依存関係の根拠となる個々の参照が含まれています。
各 Reference から、参照が発生しているファイル名、行番号、参照種別を取得できます。

```java
for (Map.Entry<Entity, Reference[]> entry : deps.entrySet()) {
    Entity depTarget = entry.getKey();
    Reference[] refs = entry.getValue();
    // 参照の詳細を表示（先頭3件）
    for (int i = 0; i < Math.min(3, refs.length); i++) {
        Reference ref = refs[i];
        System.out.printf("      %s 行%d (種別: %s)%n",
            ref.file().name(), ref.line(), ref.kind().name());
    }
}
```

### コード

```java
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
```

### 実行例

```bash
java -cp "Understand.jar;." DependencyAnalyzer sample.udb class-deps
```

### 出力例

```
=== クラス間依存関係 ===
sample.Task が依存するクラス:
  → sample.BaseItem (3箇所)
      SampleProject.java 行40 (種別: Java Extend)
      SampleProject.java 行47 (種別: Java Call)
      SampleProject.java 行70 (種別: Java Call)
  → sample.Task.Priority (2箇所)
      SampleProject.java 行43 (種別: Java Typed)
      SampleProject.java 行46 (種別: Java Typed)
sample.Task に依存するクラス:
  ← sample.TaskManager (12箇所)
  ← sample.SampleProject (6箇所)

sample.TaskManager が依存するクラス:
  → sample.Task (10箇所)
      SampleProject.java 行82 (種別: Java Typed)
      SampleProject.java 行84 (種別: Java Typed)
      SampleProject.java 行91 (種別: Java Typed)
      ... 他 7 件
sample.TaskManager に依存するクラス:
  ← sample.SampleProject (5箇所)

sample.SampleProject が依存するクラス:
  → sample.TaskManager (5箇所)
      SampleProject.java 行136 (種別: Java Typed)
      SampleProject.java 行136 (種別: Java Create)
      SampleProject.java 行138 (種別: Java Call)
      ... 他 2 件
  → sample.Task (6箇所)
      SampleProject.java 行138 (種別: Java Create)
      SampleProject.java 行139 (種別: Java Create)
      SampleProject.java 行140 (種別: Java Create)
      ... 他 3 件
```

この出力から、以下のことが読み取れます。

- `Task` クラスは `BaseItem` を継承し、`Task.Priority` を使用している
- `TaskManager` は `Task` に強く依存している（10 箇所の参照）
- `SampleProject` は `TaskManager` と `Task` の両方に依存している（メインクラスのため）
- 依存の方向は `SampleProject → TaskManager → Task → BaseItem` の階層構造になっている

---

## ユースケース3: コード構造情報のCSV一括出力

コード構造情報（クラス一覧・メソッド一覧・呼び出し関係）を CSV ファイルに一括エクスポートすることで、Excel や他のツールでの二次分析が可能になります。
ここでは Java 標準ライブラリの `PrintWriter` と `FileWriter` のみを使用し、外部 CSV ライブラリは使いません。

### 出力ファイル一覧

`csv-all` コマンドは指定ディレクトリに以下の 4 ファイルを出力します。

| ファイル | 内容 |
|---------|------|
| `classes.csv` | クラス一覧 |
| `methods.csv` | メソッド定義一覧 |
| `calls.csv` | 関数呼び出し一覧（あるメソッドが呼び出しているメソッド） |
| `calledby.csv` | 関数の被呼び出し一覧（あるメソッドを呼び出しているメソッド） |

### 各CSVのフォーマット

**classes.csv（クラス一覧）**

```csv
クラス名,種別,ファイル名,定義行
sample.Task,Java Class,SampleProject.java,38
sample.TaskManager,Java Class,SampleProject.java,78
```

**methods.csv（メソッド定義一覧）**

```csv
クラス名,メソッド名,戻り値型,ファイル名,定義行
sample.TaskManager,addTask,void,SampleProject.java,82
sample.TaskManager,getTasksByPriority,List<Task>,SampleProject.java,91
```

**calls.csv（関数呼び出し一覧）**

```csv
呼び出し元クラス,呼び出し元メソッド,呼び出し先クラス,呼び出し先メソッド,ファイル名,呼び出し行
sample.TaskManager,addTask,sample.Task,getTitle,SampleProject.java,85
sample.TaskManager,getTasksByPriority,sample.Task,getPriority,SampleProject.java,94
```

**calledby.csv（関数の被呼び出し一覧）**

```csv
対象クラス,対象メソッド,呼び出し元クラス,呼び出し元メソッド,ファイル名,呼び出し行
sample.Task,getTitle,sample.TaskManager,addTask,SampleProject.java,85
sample.Task,getPriority,sample.TaskManager,getTasksByPriority,SampleProject.java,94
```

### コード

一括出力の起点となる `exportAllCsv()` メソッドでは、出力ディレクトリの作成後、4 つの個別出力メソッドを順に呼び出します。

```java
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
}
```

各個別メソッドの実装を順に見ていきます。

#### クラス一覧の出力

クラス自身の `definein` 参照を使って、定義ファイルと行番号を取得します。

```java
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
```

#### メソッド定義一覧の出力

クラスの `define` 参照でメソッドを列挙し、各メソッドの名前・戻り値型・定義位置を出力します。

```java
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
```

#### 関数呼び出し一覧の出力

各メソッドの `call` 参照で呼び出し先メソッドを列挙します。呼び出し先の所属クラスは `definein` 参照で逆引きします。

```java
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
```

#### 関数の被呼び出し一覧の出力

`calls.csv` の逆方向です。各メソッドの `callby` 参照で、そのメソッドを呼び出しているメソッドを列挙します。

```java
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
```

#### メソッドの所属クラス名の逆引き

呼び出し先・呼び出し元のメソッドがどのクラスに属するかを `definein` 参照で逆引きするヘルパーメソッドです。

```java
/** メソッドの所属クラス名を取得するヘルパー */
private static String getOwnerClassName(Entity method) {
    // definein 参照でメソッドを定義しているクラスを逆引き
    Reference[] defInRefs = method.refs("definein", "class", true);
    if (defInRefs.length > 0) {
        return defInRefs[0].ent().longname();
    }
    return "";
}
```

### 解説

CSV 出力のポイントは以下のとおりです。

- **4 ファイル分割** — クラス・メソッド・呼び出し・被呼び出しを個別のCSVに分けることで、Excel やスプレッドシートでの二次分析（フィルタ・ピボットテーブル等）がしやすくなります。
- **`try-with-resources`** を使用しているため、`PrintWriter` は処理完了時に自動的に閉じられます。例外発生時もリソースリークを防げます。
- **`PrintWriter` + `FileWriter`** は Java 標準ライブラリのクラスです。外部ライブラリを追加する必要はありません。
- **`refs()` の使い分け** — `define` でメソッド定義、`call` / `callby` で呼び出し関係、`definein` で所属クラスの逆引きと、目的に応じて参照種別を使い分けています。

> **注意:** このサンプルでは、クラス名やメソッド名にカンマが含まれないことを前提としています。
> Java の識別子にはカンマを使用できないため、通常のプロジェクトでは問題になりません。

### 実行例

```bash
java -cp "Understand.jar;." DependencyAnalyzer sample.udb csv-all output/
```

### 出力例

```
CSVを出力しました: /path/to/output
  - classes.csv（クラス一覧）
  - methods.csv（関数定義一覧）
  - calls.csv（関数呼び出し一覧）
  - calledby.csv（関数の被呼び出し一覧）
```

4 つの CSV を組み合わせることで、プロジェクト全体のコード構造を多角的に分析できます。たとえば、`calls.csv` と `calledby.csv` を突き合わせることで、特定のメソッドの呼び出しチェーンを追跡したり、呼び出し元が多いメソッド（ハブとなるメソッド）を特定したりできます。

---

## まとめ

本章で解説した 3 つのユースケースの要点を以下にまとめます。

| ユースケース | 主な API | 用途 |
|-------------|---------|------|
| ファイル間依存関係 | `entity.depends()` | ファイル単位の依存関係を把握し、変更影響範囲を見積もる |
| クラス間依存関係 | `entity.depends()` + `entity.dependsby()` | クラス間の双方向の依存関係を分析し、結合度を評価する |
| CSV一括出力 | `entity.refs()` + `PrintWriter` | コード構造情報（クラス・メソッド・呼び出し関係）をCSVにエクスポートする |

`depends()` と `dependsby()` は [03 - コード構造の探索](03-code-exploration.md) で紹介した `refs()` の `call` / `callby` と同じく、順方向と逆方向のペアになっています。
`refs()` が個々の参照レベルで関係を取得するのに対し、`depends()` / `dependsby()` はエンティティ単位で集約された依存関係を返すため、モジュール間の結合度の分析に適しています。

---

次章: [05 - APIリファレンス](05-api-reference.md)
