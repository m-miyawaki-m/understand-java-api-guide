# 03 - コード構造の探索（Code Exploration）

Understand Java API の真価は、ソースコードの構造をプログラム的に探索できる点にあります。
本章では、`CodeExplorer.java`（[ソースコード全文](samples/CodeExplorer.java)）を使い、クラス一覧の取得からメソッド呼び出し関係の追跡、変数の参照箇所の特定、字句解析（Lexical Analysis）まで、実践的なコード探索の手法を解説します。

## CodeExplorer.java の概要

`CodeExplorer.java` は、コマンドライン引数でデータベースファイルと探索コマンドを受け取り、5 種類のコード探索を実行できるサンプルプログラムです。

### 使い方

```
java -cp "Understand.jar;." CodeExplorer <UDBファイルパス> <コマンド> [対象名]
```

### コマンド一覧

| コマンド | 説明 | 対象名 |
|---------|------|-------|
| `classes` | 全クラス一覧を表示 | 不要 |
| `methods` | 指定クラスのメソッド一覧を表示 | クラス名 |
| `calls` | メソッド呼び出し関係を表示 | メソッド名 |
| `refs` | 変数の参照箇所を表示 | 変数名 |
| `lexer` | ファイルの字句解析結果を表示 | ファイル名 |

以下の各ユースケースでは、`SampleProject.java`（[ソースコード全文](samples/SampleProject.java)）を Understand で解析して作成した `sample.udb` を対象としています。

---

## ユースケース1: 全クラス一覧の取得

最も基本的な探索は、データベース内の全クラスを一覧表示することです。

### コード

```java
/** 全クラス一覧を表示 */
private static void listClasses(Database db) {
    Entity[] classes = db.ents("class ~unknown ~unresolved");
    System.out.println("=== クラス一覧 ===");
    for (Entity cls : classes) {
        System.out.printf("  %s (種別: %s)%n", cls.longname(), cls.kind().name());
    }
    System.out.println("合計: " + classes.length + " クラス");
}
```

### 解説

`db.ents("class ~unknown ~unresolved")` は、データベース内のクラスエンティティを取得します。

この kindstring は以下のように解釈されます。

| 構成要素 | 意味 |
|---------|------|
| `class` | クラスに該当するエンティティを取得 |
| `~unknown` | Unknown（不明）のエンティティを除外 |
| `~unresolved` | Unresolved（未解決）のエンティティを除外 |

**`~unknown` と `~unresolved` で除外する理由:**
Understand はソースコード中に登場するすべての型を認識しようとします。そのため、プロジェクト外の標準ライブラリのクラス（`String`、`ArrayList` など）や、ソースコードが提供されていない外部ライブラリのクラスも Unknown や Unresolved として登録されます。
これらを除外することで、**解析対象プロジェクト内に定義されたクラスのみ**を取得できます。

### 実行例

```bash
java -cp "Understand.jar;." CodeExplorer sample.udb classes
```

### 出力例

```
=== クラス一覧 ===
  sample.BaseItem (種別: Java Abstract Class)
  sample.Task (種別: Java Class)
  sample.TaskManager (種別: Java Class)
  sample.SampleProject (種別: Java Class)
合計: 4 クラス
```

> **補足:** 出力の種別（Kind）を見ると、`BaseItem` は `Java Abstract Class`、他は `Java Class` と表示されています。
> このように Kind はクラスの性質（抽象クラスかどうかなど）も反映しています。

---

## ユースケース2: 特定クラスのメソッド一覧

特定のクラスにどのようなメソッドが定義されているかを調べます。

### コード

```java
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
```

### 解説

このメソッドの中心は `cls.refs("define", "method", true)` です。

`refs()` メソッドの 3 つの引数はそれぞれ以下の意味を持ちます。

| 引数 | 値 | 意味 |
|------|-----|------|
| `refkindstring` | `"define"` | 「定義する」という参照種別でフィルタ |
| `entkindstring` | `"method"` | 参照先がメソッドであるもののみ |
| `unique` | `true` | 同一エンティティへの重複参照を除外 |

つまり「このクラスが**定義している**メソッド」の一覧を取得しています。

取得した Reference から `ref.ent()` で参照先のメソッド Entity を取得し、`method.name()` でメソッド名、`method.type()` で戻り値型、`ref.line()` で定義行を表示しています。

### 実行例

```bash
java -cp "Understand.jar;." CodeExplorer sample.udb methods TaskManager
```

### 出力例

```
=== TaskManager のメソッド一覧 ===
  addTask (戻り値型: void, 行: 84)
  findById (戻り値型: Task, 行: 90)
  getByPriority (戻り値型: List, 行: 99)
  completeTask (戻り値型: void, 行: 109)
  printAll (戻り値型: void, 行: 116)
  countCompleted (戻り値型: int, 行: 122)
```

---

## ユースケース3: メソッド呼び出し関係

メソッド間の呼び出し関係を双方向に調べます。これはコードの影響範囲分析やリファクタリング時に特に役立ちます。

### コード

```java
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
```

### 解説

このメソッドでは `call` と `callby` という 2 つの参照種別を使い分けています。

```
call（呼び出し先）                callby（呼び出し元）

completeTask                      completeTask
    │                                 ▲
    │ refs("call", ...)               │ refs("callby", ...)
    ▼                                 │
findById                          main
```

| 参照種別 | 方向 | 意味 |
|---------|------|------|
| `call` | 順方向 | 「このメソッドが呼び出しているメソッド」を取得 |
| `callby` | 逆方向 | 「このメソッドを呼び出しているメソッド」を取得 |

この方向性の概念は Understand Java API の参照システム全体に共通しています。
`define` / `definein`、`use` / `useby`、`set` / `setby` なども同様に、順方向と逆方向のペアになっています（詳細は [02 - 主要概念](02-core-concepts.md) の Reference kindstring 一覧を参照）。

### 実行例

```bash
java -cp "Understand.jar;." CodeExplorer sample.udb calls completeTask
```

### 出力例

```
=== sample.TaskManager.completeTask が呼び出すメソッド ===
  → sample.TaskManager.findById (行: 110)
  → sample.Task.complete (行: 112)
=== sample.TaskManager.completeTask を呼び出すメソッド ===
  ← sample.SampleProject.main (行: 142)
```

この出力から、`completeTask` メソッドは `findById` と `complete` を呼び出しており、`main` メソッドから呼ばれていることがわかります。

---

## ユースケース4: 変数の参照箇所

変数がどこで定義され、どこで使用されているかを網羅的に調べます。

### コード

```java
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
```

### 解説

`ent.refs(null, null, false)` は、参照種別もエンティティ種別もフィルタせず、重複も除外しない設定です。これにより、対象変数に関する**すべての参照**を取得できます。

| 引数 | 値 | 意味 |
|------|-----|------|
| `refkindstring` | `null` | 全ての参照種別を対象 |
| `entkindstring` | `null` | 参照元のエンティティ種別を限定しない |
| `unique` | `false` | 重複する参照も含める（同じ変数が同一メソッド内で複数回使われている場合も個別に取得） |

### Reference の各属性の読み方

出力に含まれる各属性の意味は以下のとおりです。

| 属性 | メソッド | 説明 |
|------|---------|------|
| 参照元 | `ref.scope()` | この参照が発生しているスコープ（メソッドやクラス）の Entity |
| 参照種別 | `ref.kind()` | 参照の種別（定義、使用、代入など）の Kind |
| ファイル | `ref.file()` | 参照が記述されているファイルの Entity |
| 行 | `ref.line()` | 参照が発生している行番号 |
| 列 | `ref.column()` | 参照が発生している列番号 |

> **補足:** `ref.ent()` は参照先の Entity を返します。このサンプルでは変数自体の参照を調べているため、`ref.ent()` は常に対象変数を指します。一方 `ref.scope()` はその参照が行われているスコープ（メソッドやクラス）を示すため、出力には `ref.scope()` を使用しています。

### 実行例

```bash
java -cp "Understand.jar;." CodeExplorer sample.udb refs tasks
```

### 出力例

```
=== sample.TaskManager.tasks の参照箇所 ===
  sample.TaskManager (参照種別: Java Define, ファイル: SampleProject.java, 行: 82, 列: 42)
  sample.TaskManager.addTask (参照種別: Java Use, ファイル: SampleProject.java, 行: 86, 列: 13)
  sample.TaskManager.findById (参照種別: Java Use, ファイル: SampleProject.java, 行: 91, 列: 30)
  sample.TaskManager.getByPriority (参照種別: Java Use, ファイル: SampleProject.java, 行: 101, 列: 30)
  sample.TaskManager.printAll (参照種別: Java Use, ファイル: SampleProject.java, 行: 117, 列: 30)
  sample.TaskManager.countCompleted (参照種別: Java Use, ファイル: SampleProject.java, 行: 124, 列: 30)
```

参照種別に注目すると、1 行目が `Java Define`（定義）で、残りはすべて `Java Use`（使用）です。
このように、変数のライフサイクル（定義、代入、使用）を参照種別で追跡できます。

---

## ユースケース5: 字句解析

Lexer（字句解析器）を使うと、ソースファイルをトークン単位で走査し、各トークンに対応するエンティティ情報を取得できます。

### コード

```java
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
```

### 解説

#### `file.lexer(true, false, false)` の各引数

| 引数 | 値 | 意味 |
|------|-----|------|
| `lookupEnts` | `true` | 各 Lexeme に対応する Entity を関連付ける。`lex.entity()` で取得可能になる |
| `showInactive` | `false` | 非アクティブコード（`#ifdef` で除外された部分等）を含めない。Java では通常 `false` |
| `expandMacros` | `false` | マクロ展開をしない。Java では通常 `false` |

> **ポイント:** `lookupEnts` を `true` にすると、識別子トークンからその識別子が表す Entity を直接取得できます。これにより「ソースコード上のこの位置にあるシンボルが、どのクラス・メソッド・変数に対応するか」を特定できます。

#### Lexeme の走査パターン

Lexer は連結リスト構造を持ちます。`lexer.first()` で先頭の Lexeme を取得し、`lex.next()` で次のトークンに進みます。末尾に到達すると `next()` は `null` を返します。

```java
Lexeme lex = lexer.first();   // 先頭の Lexeme を取得
while (lex != null) {          // null になるまでループ
    // ... トークンの処理 ...
    lex = lex.next();          // 次の Lexeme へ
}
```

#### `token()` の代表的な値一覧

`lex.token()` はトークンの種類を文字列で返します。

| token() の値 | 意味 | 例 |
|-------------|------|-----|
| `Keyword` | Java のキーワード | `class`, `public`, `if`, `return` |
| `Identifier` | 識別子（クラス名、メソッド名、変数名など） | `Task`, `addTask`, `tasks` |
| `Literal` | 数値リテラル | `42`, `3.14` |
| `String` | 文字列リテラル | `"設計書作成"` |
| `Operator` | 演算子 | `+`, `=`, `==`, `!=` |
| `Punctuation` | 区切り文字 | `{`, `}`, `(`, `)`, `;` |
| `Comment` | コメント | `// コメント`, `/* ... */` |
| `Whitespace` | 空白（スペース、タブ） | (表示しない) |
| `Newline` | 改行 | (表示しない) |

このサンプルでは `Whitespace` と `Newline` を除外し、意味のあるトークンのみを表示しています。

#### `entity()` によるエンティティ関連付け

`lex.entity()` は、そのトークンに対応する Entity を返します（`lookupEnts=true` の場合）。
たとえば、識別子 `Task` のトークンからは `sample.Task` クラスの Entity が取得でき、出力の `→` 以降に完全修飾名として表示されます。
キーワードや演算子など、エンティティに対応しないトークンでは `null` が返ります。

### 実行例

```bash
java -cp "Understand.jar;." CodeExplorer sample.udb lexer SampleProject.java
```

### 出力例

```
=== SampleProject.java の字句解析 (先頭20トークン) ===
  行1 列0: Keyword      "package"
  行1 列8: Identifier   "sample" → sample
  行1 列14: Punctuation  ";"
  行8 列0: Comment      "// --- インターフェース ---"
  行9 列0: Keyword      "interface"
  行9 列10: Identifier   "Printable" → sample.Printable
  行9 列20: Punctuation  "{"
  行10 列4: Identifier   "String" → java.lang.String
  行10 列11: Identifier   "toDisplayString" → sample.Printable.toDisplayString
  行10 列26: Punctuation  "("
  行10 列27: Punctuation  ")"
  行10 列28: Punctuation  ";"
  行11 列0: Punctuation  "}"
  行13 列0: Comment      "// --- 基底クラス ---"
  行14 列0: Keyword      "abstract"
  行14 列9: Keyword      "class"
  行14 列15: Identifier   "BaseItem" → sample.BaseItem
  行14 列24: Keyword      "implements"
  行14 列35: Identifier   "Printable" → sample.Printable
  行14 列45: Punctuation  "{"
```

`Identifier` トークンの後に `→` で表示されている完全修飾名が、`entity()` で取得された関連エンティティです。`String` が `java.lang.String` として解決されていることや、`toDisplayString` がインターフェースのメソッドとして関連付けられていることが確認できます。

---

## まとめ

本章で解説した 5 つのユースケースの要点を以下にまとめます。

| ユースケース | 主な API | kindstring / 引数 | 用途 |
|-------------|---------|-------------------|------|
| クラス一覧 | `db.ents(kindstring)` | `"class ~unknown ~unresolved"` | プロジェクト内のクラス構成を把握 |
| メソッド一覧 | `entity.refs(refkind, entkind, unique)` | `"define"`, `"method"`, `true` | クラスの API 設計を確認 |
| 呼び出し関係 | `entity.refs(...)` | `"call"` / `"callby"` | 影響範囲分析、依存関係の調査 |
| 参照箇所 | `entity.refs(null, null, false)` | フィルタなし | 変数の使用箇所の網羅的な特定 |
| 字句解析 | `entity.lexer(...)` + Lexeme 走査 | `lookupEnts=true` | トークン単位の詳細なコード解析 |

これらの手法を組み合わせることで、大規模なコードベースでも効率的に構造を理解し、影響範囲の把握やリファクタリング対象の特定を行うことができます。

---

次章: [04 - 依存関係の分析](04-dependency-analysis.md)
