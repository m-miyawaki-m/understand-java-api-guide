# 04 - 依存関係の分析（Dependency Analysis）

ソフトウェアの品質と保守性を左右する重要な要素の一つが、モジュール間の**依存関係**です。
Understand Java API を使えば、ファイル間やクラス間の依存関係をプログラム的に抽出・分析し、以下のような課題に対応できます。

- **循環依存の検出** — モジュール A が B に依存し、B が A に依存しているような循環構造を発見する
- **変更影響範囲の把握** — あるクラスを修正した際に影響を受ける他のクラスやファイルを特定する
- **アーキテクチャの健全性チェック** — レイヤー間の依存方向が設計方針に沿っているかを検証する

本章では、`DependencyAnalyzer.java`（[ソースコード全文](samples/DependencyAnalyzer.java)）を使い、依存関係の取得からCSVエクスポート、グラフ画像の生成までを解説します。

## DependencyAnalyzer.java の概要

`DependencyAnalyzer.java` は、コマンドライン引数でデータベースファイルとコマンドを受け取り、4 種類の依存関係分析を実行できるサンプルプログラムです。

### 使い方

```
java -cp "Understand.jar;." DependencyAnalyzer <UDBファイルパス> <コマンド> [出力パス] [エンティティ名]
```

### コマンド一覧

| コマンド | 説明 | 追加引数 |
|---------|------|---------|
| `file-deps` | ファイル間依存関係を表示 | 不要 |
| `class-deps` | クラス間依存関係を表示 | 不要 |
| `csv` | 依存関係をCSVファイルに出力 | 出力パス |
| `graph` | 依存関係グラフ画像を生成 | 出力パス、エンティティ名 |

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

## ユースケース3: CSV出力

依存関係データを CSV ファイルにエクスポートすることで、Excel や他のツールでの二次分析が可能になります。
ここでは Java 標準ライブラリの `PrintWriter` と `FileWriter` のみを使用し、外部 CSV ライブラリは使いません。

### 出力フォーマット

CSV ファイルは以下の形式で出力されます。

```
依存元,依存先,参照数
```

各列の意味：

| 列 | 説明 |
|----|------|
| 依存元 | 依存元クラスの完全修飾名 |
| 依存先 | 依存先クラスの完全修飾名 |
| 参照数 | 依存元から依存先への参照の件数 |

### コード

```java
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
```

### 解説

CSV 出力のポイントは以下のとおりです。

- **`try-with-resources`** を使用しているため、`PrintWriter` は処理完了時に自動的に閉じられます。例外発生時もリソースリークを防げます。
- **`PrintWriter` + `FileWriter`** は Java 標準ライブラリのクラスです。外部ライブラリを追加する必要はありません。
- **`writer.printf("%s,%s,%d%n", ...)`** でカンマ区切りのフォーマットを直接指定しています。

> **注意:** このサンプルでは、クラスの完全修飾名にカンマが含まれないことを前提としています。
> Java のクラス名にはカンマを使用できないため、通常のプロジェクトでは問題になりません。

### 実行例

```bash
java -cp "Understand.jar;." DependencyAnalyzer sample.udb csv dependencies.csv
```

### 出力CSV例

```csv
依存元,依存先,参照数
sample.Task,sample.BaseItem,3
sample.Task,sample.Task.Priority,2
sample.TaskManager,sample.Task,10
sample.SampleProject,sample.TaskManager,5
sample.SampleProject,sample.Task,6
```

このCSVを Excel で開くと、依存関係の全体像を一覧表で確認でき、参照数でソートすることで結合度の高いクラスペアを特定できます。

---

## ユースケース4: グラフ画像生成

Understand Java API の `Entity.draw()` メソッドを使うと、エンティティの依存関係やコール関係などを画像ファイルとして出力できます。
GUI を起動せずに、プログラムからグラフ画像を自動生成できるため、CI/CD パイプラインやレポート生成に組み込むことも可能です。

### `Entity.draw()` の引数

```java
entity.draw(String graph, String filename, String options)
```

| 引数 | 説明 |
|------|------|
| `graph` | 生成するグラフの種類 |
| `filename` | 出力ファイル名。拡張子で画像形式が決まる |
| `options` | セミコロン区切りの設定文字列。`null` でデフォルト設定 |

#### `graph` に指定できるグラフの種類

| 値 | 説明 |
|----|------|
| `"DependsOn"` | 依存関係グラフ。対象エンティティが依存するエンティティを表示 |
| `"Butterfly"` | バタフライグラフ。対象を中心に依存先と被依存を両方向に表示 |
| `"Called By"` | 呼び出し元グラフ。対象メソッドを呼び出しているメソッドを表示 |
| `"Calls"` | 呼び出し先グラフ。対象メソッドが呼び出しているメソッドを表示 |
| `"Base Classes"` | 基底クラスグラフ。クラスの継承階層を表示 |
| `"Declaration"` | 宣言グラフ。クラスのメンバー構造を表示 |
| `"Control Flow"` | 制御フローグラフ。メソッド内の制御フローを表示 |

#### `filename` で対応する画像形式

| 拡張子 | 形式 |
|-------|------|
| `.jpg` | JPEG 画像 |
| `.png` | PNG 画像 |
| `.svg` | SVG（ベクター画像） |
| `.vdx` | Visio XML |

#### `options` に指定できる設定

`options` は `null` を指定するとデフォルト設定で出力されます。カスタマイズする場合はセミコロン区切りで設定を記述します。

| 設定 | 説明 | 例 |
|------|------|----|
| `Layout` | レイアウトアルゴリズム | `"Layout=Crossing"` |
| `name` | ノードに表示する名前の形式 | `"name=Fullname"` |
| `Level` | 表示する階層の深さ | `"Level=AllLevels"` |

複数の設定を組み合わせる例：

```java
entity.draw("DependsOn", "output.png", "Layout=Crossing;name=Fullname;Level=AllLevels");
```

### コード

```java
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
            ent.draw("DependsOn", outputPath, null);
            System.out.println("グラフを出力しました: " + outputPath);
            return;
        }
    }
    System.out.println("エンティティが見つかりません: " + entityName);
}
```

### 解説

このメソッドでは、指定されたエンティティ名に一致するクラスを検索し、`draw()` メソッドで依存関係グラフを生成しています。

- エンティティの検索は `longname()`（完全修飾名）と `name()`（短縮名）の両方で照合するため、`"sample.TaskManager"` でも `"TaskManager"` でも指定できます
- `draw()` の第 3 引数に `null` を渡しているため、デフォルト設定でグラフが生成されます
- 出力ファイルの拡張子によって画像形式が自動的に決まります

### 実行例

```bash
# PNG 形式で依存関係グラフを出力
java -cp "Understand.jar;." DependencyAnalyzer sample.udb graph deps.png TaskManager

# SVG 形式で出力
java -cp "Understand.jar;." DependencyAnalyzer sample.udb graph deps.svg sample.TaskManager
```

> **ヒント:** SVG 形式はベクター画像のため、拡大しても劣化しません。ドキュメントへの埋め込みや Web での表示に適しています。

---

## まとめ

本章で解説した 4 つのユースケースの要点を以下にまとめます。

| ユースケース | 主な API | 用途 |
|-------------|---------|------|
| ファイル間依存関係 | `entity.depends()` | ファイル単位の依存関係を把握し、変更影響範囲を見積もる |
| クラス間依存関係 | `entity.depends()` + `entity.dependsby()` | クラス間の双方向の依存関係を分析し、結合度を評価する |
| CSV出力 | `PrintWriter` + `FileWriter` | 依存関係データを外部ツールで二次分析できる形式でエクスポートする |
| グラフ画像生成 | `entity.draw(graph, filename, options)` | 依存関係を視覚的に表現し、レポートやドキュメントに活用する |

`depends()` と `dependsby()` は [03 - コード構造の探索](03-code-exploration.md) で紹介した `refs()` の `call` / `callby` と同じく、順方向と逆方向のペアになっています。
`refs()` が個々の参照レベルで関係を取得するのに対し、`depends()` / `dependsby()` はエンティティ単位で集約された依存関係を返すため、モジュール間の結合度の分析に適しています。

---

次章: [05 - APIリファレンス](05-api-reference.md)
