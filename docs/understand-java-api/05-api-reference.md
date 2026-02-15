# Understand Java API リファレンス

> `com.scitools.understand` パッケージの全クラス・メソッドの日本語リファレンス。

本ドキュメントでは、Understand Java API を構成する 8 つのクラスのすべてのメソッドについて、シグネチャ、引数、戻り値、例外、使用例を記載しています。
各クラス・メソッド名は原文（英語）のまま、説明は日本語で記述しています。

---

## 目次

1. [Understand クラス](#1-understand-クラス) — エントリーポイント
2. [Database クラス](#2-database-クラス) — データベース操作
3. [Entity クラス](#3-entity-クラス) — ソースコード要素
4. [Reference クラス](#4-reference-クラス) — エンティティ間の参照
5. [Kind クラス](#5-kind-クラス) — 種別の分類
6. [Lexer クラス](#6-lexer-クラス) — 字句解析器
7. [Lexeme クラス](#7-lexeme-クラス) — 字句（トークン）
8. [UnderstandException クラス](#8-understandexception-クラス) — 例外

- [付録: Java向け kindstring フィルタ一覧](#付録-java向け-kindstring-フィルタ一覧)

---

## 1. Understand クラス

```
com.scitools.understand.Understand
```

Understand Java API のエントリーポイントとなるクラスです。
データベースを開くための `open()` メソッドを提供します。`Database` オブジェクトを取得する唯一の手段であり、すべての API 操作はここから始まります。

### メソッド一覧

| メソッド | 説明 |
|---------|------|
| `open(String name)` | データベースを開く |
| `metriclist(String kindstring)` | 有効なメトリクス名の一覧を取得する |
| `loadNativeLibrary()` | ネイティブ JNI コンポーネントをロードする |

### メソッド詳細

#### `static Database open(String name) throws UnderstandException`

指定されたパスの Understand データベース（`.udb` ファイル）を開き、`Database` オブジェクトを返します。
`Database` オブジェクトを取得する唯一の方法です。同時に開けるデータベースは 1 つだけです。

| 項目 | 内容 |
|------|------|
| 引数 | `name` - データベースファイル（`.udb`）のパス |
| 戻り値 | `Database` - 開いたデータベースオブジェクト |
| 例外 | `UnderstandException` - `DBAlreadyOpen`（既に別のデータベースが開いている）、`DBCorrupt`（データベースが破損）、`DBOldVersion`（古いバージョン）、`DBUnknownVersion`（不明なバージョン）、`DBUnableOpen`（ファイルを開けない）、`NoApiLicense`（API ライセンスが無効） |

**使用例:**
```java
Database db = Understand.open("project.udb");
// ... 処理 ...
db.close();
```

---

#### `static String[] metriclist(String kindstring)`

有効なメトリクス名の一覧を文字列配列で返します。
`kindstring` を指定すると、その種別に該当するエンティティに対して有効なメトリクスのみが返されます。

| 項目 | 内容 |
|------|------|
| 引数 | `kindstring` - エンティティの種別フィルタ。`null` を指定するとすべてのメトリクスを返す |
| 戻り値 | `String[]` - メトリクス名の配列 |

**使用例:**
```java
String[] allMetrics = Understand.metriclist(null);
String[] classMetrics = Understand.metriclist("class");
```

---

#### `static void loadNativeLibrary()`

Understand Java API が内部で使用するネイティブ JNI コンポーネントをロードします。
通常は自動的に呼び出されるため、明示的に呼び出す必要はありません。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `void` |

**使用例:**
```java
Understand.loadNativeLibrary(); // 通常は不要（自動呼び出し）
```

---

## 2. Database クラス

```
com.scitools.understand.Database
```

Understand が解析したソースコードの情報全体を保持するクラスです。
エンティティの取得、メトリクスの計算、データベースの管理などの機能を提供します。
`Understand.open()` でのみ取得でき、使用後は必ず `close()` で閉じる必要があります。

### メソッド一覧

| メソッド | 説明 |
|---------|------|
| `name()` | データベースのファイル名を取得する |
| `close()` | データベースを閉じる |
| `ents(String kinds)` | エンティティの一覧を取得する |
| `lookup_uniquename(String uniquename)` | 一意な名前でエンティティを検索する |
| `language()` | 有効な解析言語の一覧を取得する |
| `metrics()` | 利用可能なメトリクス名の一覧を取得する |
| `metric(String name)` | 単一のメトリクス値を取得する |
| `metric(String[] names)` | 複数のメトリクス値を一括取得する |
| `metrics_treemap(String sizemetric, String colormetric, String enttype)` | ツリーマップ画像を生成する |
| `comparison_db()` | 比較用データベースを取得する |

### メソッド詳細

#### `String name()`

データベースのファイル名（パス）を文字列で返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - データベースファイルのパス |

**使用例:**
```java
Database db = Understand.open("project.udb");
System.out.println("データベース: " + db.name());
```

---

#### `void close()`

データベースを閉じ、関連するすべてのメモリを解放します。
使用が完了したら**必ず**呼び出してください。既に閉じられたデータベースに対して呼び出しても安全です。
`close()` 後は、このデータベースから取得した Entity、Reference、Kind、Lexer、Lexeme などのオブジェクトはすべて無効になります。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `void` |

**使用例:**
```java
Database db = Understand.open("project.udb");
try {
    // ... 処理 ...
} finally {
    db.close();
}
```

---

#### `Entity[] ents(String kinds)`

データベース内のエンティティを配列で返します。
`kinds` に kindstring フィルタを指定すると、該当する種別のエンティティのみが返されます。

| 項目 | 内容 |
|------|------|
| 引数 | `kinds` - kindstring フィルタ。`null` を指定すると全エンティティを返す |
| 戻り値 | `Entity[]` - 条件に一致するエンティティの配列 |

**使用例:**
```java
Entity[] all = db.ents(null);                          // 全エンティティ
Entity[] classes = db.ents("class ~unknown ~unresolved"); // クラスのみ
Entity[] methods = db.ents("method");                   // メソッドのみ
```

---

#### `Entity lookup_uniquename(String uniquename)`

一意な名前（uniquename）でエンティティを検索し、一致する Entity を返します。
見つからない場合は `null` を返します。

| 項目 | 内容 |
|------|------|
| 引数 | `uniquename` - エンティティの一意な名前（`Entity.uniquename()` で取得した値） |
| 戻り値 | `Entity` - 一致するエンティティ。見つからない場合は `null` |

**使用例:**
```java
String uname = entity.uniquename(); // あらかじめ保存しておいた uniquename
Entity found = db.lookup_uniquename(uname);
if (found != null) {
    System.out.println("見つかりました: " + found.longname());
}
```

---

#### `String[] language()`

データベースに登録されている解析対象言語の名前を文字列配列で返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String[]` - 言語名の配列（例: `["Java"]`） |

**使用例:**
```java
String[] langs = db.language();
System.out.println("解析言語: " + String.join(", ", langs));
```

---

#### `String[] metrics()`

データベースレベルで利用可能なメトリクス名の一覧を文字列配列で返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String[]` - メトリクス名の配列 |

**使用例:**
```java
String[] available = db.metrics();
for (String m : available) {
    System.out.println("メトリクス: " + m);
}
```

---

#### `Number metric(String name)`

指定されたメトリクスの値を返します。
該当するメトリクスの値が存在しない場合は `0` を返します。

| 項目 | 内容 |
|------|------|
| 引数 | `name` - メトリクス名 |
| 戻り値 | `Number` - メトリクスの値。値がない場合は `0` |

**使用例:**
```java
Number loc = db.metric("CountLineCode");
System.out.println("コード行数: " + loc);
```

---

#### `Map<String, Number> metric(String[] names)`

複数のメトリクスの値をまとめて取得し、メトリクス名をキー、値を値とする Map で返します。

| 項目 | 内容 |
|------|------|
| 引数 | `names` - メトリクス名の配列 |
| 戻り値 | `Map<String, Number>` - メトリクス名と値のマップ |

**使用例:**
```java
Map<String, Number> vals = db.metric(new String[]{"CountLineCode", "CountLineBlank"});
System.out.println("コード行: " + vals.get("CountLineCode"));
System.out.println("空白行: " + vals.get("CountLineBlank"));
```

---

#### `byte[] metrics_treemap(String sizemetric, String colormetric, String enttype)`

指定されたメトリクスに基づいてツリーマップ画像（JPG 形式）を生成し、バイト配列として返します。
面積がサイズメトリクスを、色がカラーメトリクスを表します。

| 項目 | 内容 |
|------|------|
| 引数 | `sizemetric` - 面積に使用するメトリクス名 |
|      | `colormetric` - 色に使用するメトリクス名 |
|      | `enttype` - エンティティの種類。`"file"`、`"class"`、`"function"` のいずれか |
| 戻り値 | `byte[]` - JPG 画像データのバイト配列 |

**使用例:**
```java
byte[] jpg = db.metrics_treemap("CountLineCode", "MaxCyclomatic", "class");
Files.write(Paths.get("treemap.jpg"), jpg);
```

---

#### `Database comparison_db()`

比較用データベースが設定されている場合、そのデータベースオブジェクトを返します。
設定されていない場合は `null` を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Database` - 比較用データベース。設定されていない場合は `null` |

**使用例:**
```java
Database compDb = db.comparison_db();
if (compDb != null) {
    System.out.println("比較DB: " + compDb.name());
}
```

---

## 3. Entity クラス

```
com.scitools.understand.Entity
```

ソースコード中のクラス、メソッド、変数、ファイル、パッケージなどの構成要素を表すクラスです。
Understand が解析して認識した名前付き要素はすべて Entity として表現されます。
Entity は `Database.ents()` や `Database.lookup_uniquename()` で取得します。

### メソッド一覧

| メソッド | 説明 |
|---------|------|
| `id()` | 数値 ID を取得する |
| `name()` | 短い名前を取得する |
| `uniquename()` | 一意な名前を取得する |
| `longname()` | 完全修飾名を取得する |
| `kind()` | エンティティの種別を取得する |
| `type()` | 型テキストを取得する |
| `language()` | 言語テキストを取得する |
| `library()` | ライブラリテキストを取得する |
| `value()` | 値テキストを取得する |
| `parameters()` | パラメータテキストを取得する |
| `contents()` | エンティティの内容を取得する |
| `parent()` | 親エンティティを取得する |
| `freetext(String kind)` | フリーテキストを取得する |
| `comments(boolean after, boolean raw, String kinds)` | コメントを取得する |
| `refs(String refkindstring, String entkindstring, boolean unique)` | 参照の一覧を取得する |
| `filerefs(String refkindstring, String entkindstring, boolean unique)` | ファイル内の参照を取得する |
| `ents(String refkindstring, String entkindstring)` | 関連エンティティを取得する |
| `metrics()` | 利用可能なメトリクス名を取得する |
| `metric(String name)` | 単一のメトリクス値を取得する |
| `metric(String[] names)` | 複数のメトリクス値を一括取得する |
| `draw(String graph, String filename, String options)` | グラフ画像を生成する |
| `ib(String options)` | 情報ブラウザの内容を取得する |
| `lexer(boolean lookupEnts, boolean showInactive, boolean expandMacros)` | 字句解析器を生成する |
| `depends()` | 依存先を取得する |
| `dependsby()` | 被依存元を取得する |

### メソッド詳細

#### `int id()`

エンティティの数値 ID を返します。
この ID はデータベース内で一意ですが、データベースの再解析や更新を行うと変わる可能性があるため、永続的な識別子としては使用しないでください。永続的な識別には `uniquename()` を使用します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `int` - エンティティの数値 ID |

**使用例:**
```java
Entity ent = db.ents("class")[0];
System.out.println("ID: " + ent.id());
```

---

#### `String name()`

エンティティの短い名前を返します。
クラス名やメソッド名など、パッケージ名を含まない単純な名前です。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - エンティティの短い名前（例: `"Task"`） |

**使用例:**
```java
Entity cls = db.ents("class")[0];
System.out.println("クラス名: " + cls.name());
```

---

#### `String uniquename()`

エンティティの一意な名前を返します。
データベース内で必ず一意になる識別子で、`Database.lookup_uniquename()` でエンティティを再取得する際に使用します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - エンティティの一意な名前 |

**使用例:**
```java
String uname = entity.uniquename();
// 後で同じエンティティを再取得
Entity same = db.lookup_uniquename(uname);
```

---

#### `String longname()`

エンティティの完全修飾名（長い名前）を返します。
パッケージ名を含む名前（例: `sample.Task`）が返されます。完全修飾名がない場合は `name()` と同じ値が返されます。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - エンティティの完全修飾名（例: `"sample.Task"`） |

**使用例:**
```java
for (Entity cls : db.ents("class")) {
    System.out.println(cls.longname()); // 例: sample.Task
}
```

---

#### `Kind kind()`

エンティティの種別（Kind）を返します。
種別はエンティティがクラスなのか、メソッドなのか、変数なのかといった分類情報を表します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Kind` - エンティティの種別オブジェクト |

**使用例:**
```java
Entity ent = db.ents("class")[0];
System.out.println("種別: " + ent.kind().name()); // 例: Java Class
```

---

#### `String type()`

エンティティの型テキストを返します。
変数の場合はデータ型（例: `String`、`int`）、メソッドの場合は戻り値型が返されます。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - 型テキスト |

**使用例:**
```java
for (Entity var : db.ents("variable")) {
    System.out.printf("%s : %s%n", var.name(), var.type());
}
```

---

#### `String language()`

エンティティが属する言語を文字列で返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - 言語名テキスト（例: `"Java"`） |

**使用例:**
```java
System.out.println("言語: " + entity.language());
```

---

#### `String library()`

エンティティが属するライブラリを文字列で返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - ライブラリテキスト |

**使用例:**
```java
System.out.println("ライブラリ: " + entity.library());
```

---

#### `String value()`

エンティティの値テキストを返します。
定数や列挙値など、値を持つエンティティで使用されます。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - 値テキスト |

**使用例:**
```java
for (Entity e : db.ents("enum")) {
    System.out.printf("%s = %s%n", e.name(), e.value());
}
```

---

#### `String parameters()`

エンティティのパラメータテキストを返します。
メソッドの場合、パラメータの型情報が文字列として返されます。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - パラメータテキスト |

**使用例:**
```java
for (Entity method : db.ents("method")) {
    System.out.printf("%s(%s)%n", method.name(), method.parameters());
}
```

---

#### `String contents()`

エンティティの内容（ソースコードテキスト）を返します。
内容がない場合は空文字列を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - エンティティの内容。なければ空文字列 |

**使用例:**
```java
Entity method = db.ents("method")[0];
String src = method.contents();
System.out.println("ソース:\n" + src);
```

---

#### `Entity parent()`

エンティティの親エンティティを返します。
例えば、メソッドの親はそれを定義しているクラス、内部クラスの親は外側のクラスです。親がない場合は `null` を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Entity` - 親エンティティ。なければ `null` |

**使用例:**
```java
Entity method = db.ents("method")[0];
Entity parent = method.parent();
if (parent != null) {
    System.out.println("所属クラス: " + parent.longname());
}
```

---

#### `String freetext(String kind)`

指定された種類のフリーテキストを返します。
フリーテキストが存在しない場合は空文字列を返します。

| 項目 | 内容 |
|------|------|
| 引数 | `kind` - フリーテキストの種類 |
| 戻り値 | `String` - フリーテキスト。なければ空文字列 |

**使用例:**
```java
String text = entity.freetext("comments");
```

---

#### `String[] comments(boolean after, boolean raw, String kinds)`

エンティティに関連付けられたコメントを文字列配列で返します。

| 項目 | 内容 |
|------|------|
| 引数 | `after` - `true` でエンティティの後のコメント、`false` で前のコメント |
|      | `raw` - `true` でコメント区切り文字（`//`、`/* */` 等）を含める |
|      | `kinds` - 対象とする宣言の kindstring |
| 戻り値 | `String[]` - コメント文字列の配列 |

**使用例:**
```java
String[] comments = entity.comments(false, false, null);
for (String c : comments) {
    System.out.println("コメント: " + c);
}
```

---

#### `Reference[] refs(String refkindstring, String entkindstring, boolean unique)`

エンティティに関連する参照（Reference）の一覧を配列で返します。
kindstring フィルタで参照種別やエンティティ種別を絞り込むことができます。

| 項目 | 内容 |
|------|------|
| 引数 | `refkindstring` - 参照種別の kindstring フィルタ。`null` で全種別 |
|      | `entkindstring` - 参照先エンティティ種別の kindstring フィルタ。`null` で全種別 |
|      | `unique` - `true` にすると、同一エンティティへの参照を最初の 1 件のみに制限する |
| 戻り値 | `Reference[]` - 条件に一致する参照の配列 |

**使用例:**
```java
// メソッドの呼び出し先を取得（重複なし）
Reference[] calls = method.refs("call", "method", true);
// すべての参照を取得（フィルタなし）
Reference[] allRefs = entity.refs(null, null, false);
```

---

#### `Reference[] filerefs(String refkindstring, String entkindstring, boolean unique)`

ファイルエンティティ内の参照を配列で返します。
ファイル以外のエンティティに対して呼び出した場合は空の配列を返します。

| 項目 | 内容 |
|------|------|
| 引数 | `refkindstring` - 参照種別の kindstring フィルタ。`null` で全種別 |
|      | `entkindstring` - 参照先エンティティ種別の kindstring フィルタ。`null` で全種別 |
|      | `unique` - `true` にすると、同一エンティティへの参照を最初の 1 件のみに制限する |
| 戻り値 | `Reference[]` - ファイル内の参照の配列。ファイル以外のエンティティでは空配列 |

**使用例:**
```java
Entity file = db.ents("file")[0];
Reference[] fileRefs = file.filerefs("call", "method", true);
```

---

#### `Entity[] ents(String refkindstring, String entkindstring)`

指定した参照種別で関連するエンティティを配列で返します。
`refs()` で参照を取得して `ent()` で個別にエンティティを取り出す代わりに、関連エンティティを直接取得できる便利メソッドです。

| 項目 | 内容 |
|------|------|
| 引数 | `refkindstring` - 参照種別の kindstring フィルタ |
|      | `entkindstring` - エンティティ種別の kindstring フィルタ |
| 戻り値 | `Entity[]` - 関連エンティティの配列 |

**使用例:**
```java
// クラスが呼び出しているメソッドを取得
Entity[] calledMethods = cls.ents("call", "method");
```

---

#### `String[] metrics()`

このエンティティに対して利用可能なメトリクス名の一覧を文字列配列で返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String[]` - メトリクス名の配列 |

**使用例:**
```java
String[] available = entity.metrics();
for (String m : available) {
    System.out.println("メトリクス: " + m);
}
```

---

#### `Number metric(String name)`

指定されたメトリクスの値を返します。
該当するメトリクスの値が存在しない場合は `0` を返します。

| 項目 | 内容 |
|------|------|
| 引数 | `name` - メトリクス名 |
| 戻り値 | `Number` - メトリクスの値。値がない場合は `0` |

**使用例:**
```java
Number complexity = method.metric("Cyclomatic");
System.out.println("循環的複雑度: " + complexity);
```

---

#### `Map<String, Number> metric(String[] names)`

複数のメトリクスの値をまとめて取得し、メトリクス名をキー、値を値とする Map で返します。

| 項目 | 内容 |
|------|------|
| 引数 | `names` - メトリクス名の配列 |
| 戻り値 | `Map<String, Number>` - メトリクス名と値のマップ |

**使用例:**
```java
Map<String, Number> vals = method.metric(new String[]{"Cyclomatic", "CountLineCode"});
System.out.println("複雑度: " + vals.get("Cyclomatic"));
System.out.println("コード行: " + vals.get("CountLineCode"));
```

---

#### `void draw(String graph, String filename, String options) throws UnderstandException`

エンティティのグラフ画像を生成し、指定されたファイルに保存します。
出力ファイルの拡張子で画像形式が決まります。

| 項目 | 内容 |
|------|------|
| 引数 | `graph` - グラフの種類（下表参照） |
|      | `filename` - 出力ファイル名。拡張子は `.jpg`、`.png`、`.vdx`、`.svg` のいずれか |
|      | `options` - セミコロン区切りの `"name=value"` 形式の設定文字列。`null` でデフォルト |
| 戻り値 | `void` |
| 例外 | `UnderstandException` - `NoFont`（フォントなし）、`NoImage`（画像生成不可）、`TooBig`（グラフが大きすぎる）、`UnableCreateFile`（ファイル作成不可）、`UnsupportedFile`（未対応のファイル形式） |

**`graph` に指定できる値:**

| 値 | 説明 |
|----|------|
| `"Base Classes"` | 基底クラスの継承階層 |
| `"Butterfly"` | 双方向の依存関係（バタフライ） |
| `"Called By"` | 呼び出し元グラフ |
| `"Calls"` | 呼び出し先グラフ |
| `"Control Flow"` | 制御フローグラフ |
| `"Declaration"` | 宣言・メンバー構造 |
| `"DependsOn"` | 依存関係グラフ |

**使用例:**
```java
Entity cls = db.ents("class")[0];
cls.draw("DependsOn", "deps.png", null);
cls.draw("Butterfly", "butterfly.svg", "Layout=Crossing;name=Fullname");
```

---

#### `String[] ib(String options)`

エンティティの情報ブラウザ（Information Browser）の内容を文字列配列で返します。
Understand GUI の情報ブラウザと同等の情報をプログラムから取得できます。

`options` はセミコロン区切りの `"name=value"` 形式、またはフィールド固有の設定として `"{field-name}name=value"` 形式で指定します。

| 項目 | 内容 |
|------|------|
| 引数 | `options` - セミコロン区切りの設定文字列 |
| 戻り値 | `String[]` - 情報ブラウザの各行を要素とする文字列配列 |

**`options` で指定できる主な設定:**

| 設定名 | 説明 |
|--------|------|
| `Indent` | インデント幅 |
| `Defnfile` | 定義ファイルの表示 |
| `Dotrefs` | 参照のドット表記 |
| `Filenames` | ファイル名の表示 |
| `Fullname` | 完全修飾名の表示 |
| `Inactives` | 非アクティブコードの表示 |
| `Levels` | 表示階層数 |
| `Parameters` | パラメータの表示 |
| `References` | 参照情報の表示 |
| `Returntypes` | 戻り値型の表示 |
| `Sort` | ソート順 |
| `Types` | 型情報の表示 |

**使用例:**
```java
String[] info = entity.ib("Fullname=on;Parameters=on");
for (String line : info) {
    System.out.println(line);
}
```

---

#### `Lexer lexer(boolean lookupEnts, boolean showInactive, boolean expandMacros) throws UnderstandException`

ファイルエンティティから字句解析器（Lexer）を生成して返します。
ファイル以外のエンティティ、解析後に変更されたファイル、読み取り不能なファイルに対して呼び出すと例外がスローされます。

| 項目 | 内容 |
|------|------|
| 引数 | `lookupEnts` - `true` にすると各 Lexeme に対応する Entity を関連付ける |
|      | `showInactive` - `true` にすると非アクティブコード（`#ifdef` で除外された部分）を含める |
|      | `expandMacros` - `true` にするとマクロを展開する |
| 戻り値 | `Lexer` - 字句解析器オブジェクト |
| 例外 | `UnderstandException` - ファイルエンティティでない場合、解析後にファイルが変更された場合、ファイルが読み取れない場合 |

> **注意:** Java では `showInactive` と `expandMacros` は主に C/C++ 向けの機能です。Java で使用する場合は通常 `false` を指定します。

**使用例:**
```java
Entity file = db.ents("file")[0];
Lexer lexer = file.lexer(true, false, false);
Lexeme lex = lexer.first();
```

---

#### `Map<Entity, Reference[]> depends()`

このエンティティが依存している他のエンティティを、依存先をキー、依存関係の根拠となる参照の配列を値とする Map で返します。
クラスやファイルエンティティに対して使用することで、モジュール間の依存関係を分析できます。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Map<Entity, Reference[]>` - 依存先エンティティと、その依存関係を構成する参照のマップ |

**使用例:**
```java
Map<Entity, Reference[]> deps = cls.depends();
for (Map.Entry<Entity, Reference[]> entry : deps.entrySet()) {
    System.out.printf("%s に依存 (%d箇所)%n",
        entry.getKey().longname(), entry.getValue().length);
}
```

---

#### `Map<Entity, Reference[]> dependsby()`

このエンティティに依存している他のエンティティを、依存元をキー、依存関係の根拠となる参照の配列を値とする Map で返します。
`depends()` の逆方向で、変更影響範囲の分析に使用します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Map<Entity, Reference[]>` - 依存元エンティティと、その依存関係を構成する参照のマップ |

**使用例:**
```java
Map<Entity, Reference[]> depsBy = cls.dependsby();
for (Map.Entry<Entity, Reference[]> entry : depsBy.entrySet()) {
    System.out.printf("%s が依存 (%d箇所)%n",
        entry.getKey().longname(), entry.getValue().length);
}
```

---

## 4. Reference クラス

```
com.scitools.understand.Reference
```

エンティティ間の参照関係を表すクラスです。
「メソッド A がメソッド B を呼び出している」「クラス C がインターフェース D を実装している」といった関係が Reference として記録されます。
Reference は `Entity.refs()` や `Entity.filerefs()` で取得します。

### メソッド一覧

| メソッド | 説明 |
|---------|------|
| `line()` | 参照が発生している行番号を取得する |
| `column()` | 参照が発生している列番号を取得する |
| `ent()` | 参照先エンティティを取得する |
| `file()` | 参照が記述されているファイルを取得する |
| `scope()` | 参照元エンティティを取得する |
| `kind()` | 参照の種別を取得する |
| `isforward()` | 前方参照かどうかを判定する |

### メソッド詳細

#### `int line()`

参照が発生しているソースコードの行番号を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `int` - 行番号 |

**使用例:**
```java
Reference ref = entity.refs("call", null, true)[0];
System.out.println("行: " + ref.line());
```

---

#### `int column()`

参照が発生しているソースコードの列番号を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `int` - 列番号 |

**使用例:**
```java
Reference ref = entity.refs("call", null, true)[0];
System.out.println("位置: 行" + ref.line() + " 列" + ref.column());
```

---

#### `Entity ent()`

参照先のエンティティ（ターゲット）を返します。
例えば、メソッド呼び出しの参照であれば、呼び出されるメソッドの Entity が返されます。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Entity` - 参照先エンティティ |

**使用例:**
```java
for (Reference ref : method.refs("call", null, true)) {
    System.out.println("呼び出し先: " + ref.ent().longname());
}
```

---

#### `Entity file()`

参照が記述されているファイルのエンティティを返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Entity` - ファイルエンティティ |

**使用例:**
```java
Reference ref = entity.refs("call", null, true)[0];
System.out.println("ファイル: " + ref.file().name());
```

---

#### `Entity scope()`

参照元のエンティティ（ソース）を返します。
参照を行っている側のエンティティ（参照の起点）です。例えば、メソッド呼び出しの参照であれば、呼び出しを行っているメソッドの Entity が返されます。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Entity` - 参照元エンティティ |

**使用例:**
```java
for (Reference ref : method.refs("callby", null, true)) {
    System.out.println("呼び出し元: " + ref.scope().longname());
}
```

---

#### `Kind kind()`

参照の種別（Kind）を返します。
参照が呼び出しなのか、定義なのか、使用なのかといった分類情報を表します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Kind` - 参照の種別オブジェクト |

**使用例:**
```java
Reference ref = entity.refs(null, null, false)[0];
System.out.println("参照種別: " + ref.kind().name()); // 例: Java Call
```

---

#### `boolean isforward()`

この参照が前方参照かどうかを返します。
前方参照とは、参照先がソースコード上で参照元よりも後に定義されている場合の参照です。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `boolean` - 前方参照の場合は `true` |

**使用例:**
```java
Reference ref = entity.refs(null, null, false)[0];
if (ref.isforward()) {
    System.out.println("前方参照です");
}
```

---

## 5. Kind クラス

```
com.scitools.understand.Kind
```

エンティティや参照の分類（種別）を表すクラスです。
Entity が「Java Class」なのか「Java Method」なのか、Reference が「Java Call」なのか「Java Define」なのかといった分類情報を保持します。
`Entity.kind()` や `Reference.kind()` で取得します。

### メソッド一覧

| メソッド | 説明 |
|---------|------|
| `name()` | 種別名を取得する |
| `check(String kindstring)` | kindstring フィルタとの一致を判定する |
| `toString()` | 文字列表現を取得する |

### メソッド詳細

#### `String name()`

種別の名前を文字列で返します（例: `"Java Class"`、`"Java Call"`）。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - 種別の名前 |

**使用例:**
```java
Entity ent = db.ents("class")[0];
System.out.println("種別: " + ent.kind().name()); // 例: Java Class
```

---

#### `boolean check(String kindstring)`

この種別が指定された kindstring フィルタに一致するかどうかを返します。
`db.ents()` や `entity.refs()` でフィルタを指定する代わりに、取得後に個別に種別を判定したい場合に使用します。

| 項目 | 内容 |
|------|------|
| 引数 | `kindstring` - kindstring フィルタ文字列 |
| 戻り値 | `boolean` - 一致する場合は `true` |

**使用例:**
```java
Entity ent = db.ents(null)[0];
if (ent.kind().check("class")) {
    System.out.println(ent.name() + " はクラスです");
}
if (ent.kind().check("method public")) {
    System.out.println(ent.name() + " は public メソッドです");
}
```

---

#### `String toString()`

種別の文字列表現を返します。`name()` と同じ値を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - 種別の文字列表現 |

**使用例:**
```java
System.out.println("種別: " + entity.kind()); // toString() が暗黙的に呼ばれる
```

---

## 6. Lexer クラス

```
com.scitools.understand.Lexer
```

ソースファイルを字句（トークン）単位で解析するための字句解析器クラスです。
`Entity.lexer()` でファイルエンティティから取得し、Lexeme の連結リストとしてソースコードの各トークンにアクセスできます。

### メソッド一覧

| メソッド | 説明 |
|---------|------|
| `first()` | 最初の Lexeme を取得する |
| `lexeme(int line, int column)` | 指定位置の Lexeme を取得する |
| `lexemes(int startLine, int endLine)` | 指定行範囲の Lexeme を取得する |
| `lines()` | ファイルの行数を取得する |

### メソッド詳細

#### `Lexeme first()`

ファイルの最初の Lexeme を返します。
返された Lexeme から `next()` を繰り返し呼び出すことで、ファイル全体のトークンを走査できます。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Lexeme` - 最初の Lexeme |

**使用例:**
```java
Lexer lexer = fileEntity.lexer(true, false, false);
Lexeme lex = lexer.first();
while (lex != null) {
    System.out.println(lex.token() + ": " + lex.text());
    lex = lex.next();
}
```

---

#### `Lexeme lexeme(int line, int column)`

指定された行と列の位置にある Lexeme を返します。
該当する位置に Lexeme がない場合は `null` を返します。

| 項目 | 内容 |
|------|------|
| 引数 | `line` - 行番号 |
|      | `column` - 列番号 |
| 戻り値 | `Lexeme` - 指定位置の Lexeme。見つからない場合は `null` |

**使用例:**
```java
Lexer lexer = fileEntity.lexer(true, false, false);
Lexeme lex = lexer.lexeme(10, 5);
if (lex != null) {
    System.out.println("10行5列のトークン: " + lex.text());
}
```

---

#### `Lexeme[] lexemes(int startLine, int endLine)`

指定された行範囲内の Lexeme を配列で返します。

| 項目 | 内容 |
|------|------|
| 引数 | `startLine` - 開始行番号 |
|      | `endLine` - 終了行番号 |
| 戻り値 | `Lexeme[]` - 指定行範囲内の Lexeme の配列 |

**使用例:**
```java
Lexer lexer = fileEntity.lexer(true, false, false);
Lexeme[] lexemes = lexer.lexemes(1, 10);
for (Lexeme lex : lexemes) {
    System.out.println(lex.token() + ": " + lex.text());
}
```

---

#### `int lines()`

ファイルの総行数を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `int` - ファイルの行数 |

**使用例:**
```java
Lexer lexer = fileEntity.lexer(false, false, false);
System.out.println("総行数: " + lexer.lines());
```

---

## 7. Lexeme クラス

```
com.scitools.understand.Lexeme
```

ソースコードの字句（トークン）を表すクラスです。
Lexeme は双方向連結リストを形成しており、`next()` で次のトークン、`previous()` で前のトークンに移動できます。
各 Lexeme はトークンの種類、テキスト、位置情報、関連する Entity などの情報を持ちます。

### メソッド一覧

| メソッド | 説明 |
|---------|------|
| `text()` | トークンのテキストを取得する |
| `token()` | トークンの種類名を取得する |
| `next()` | 次の Lexeme を取得する |
| `previous()` | 前の Lexeme を取得する |
| `entity()` | 関連する Entity を取得する |
| `reference()` | 関連する Reference を取得する |
| `lineBegin()` | 開始行番号を取得する |
| `lineEnd()` | 終了行番号を取得する |
| `columnBegin()` | 開始列番号を取得する |
| `columnEnd()` | 終了列番号を取得する |
| `inactive()` | 非アクティブかどうかを判定する |

### メソッド詳細

#### `String text()`

Lexeme のテキスト内容を返します。ソースコード上に記述されている文字列そのものです。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - トークンのテキスト |

**使用例:**
```java
Lexeme lex = lexer.first();
System.out.println("テキスト: " + lex.text());
```

---

#### `String token()`

トークンの種類名を文字列で返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `String` - トークンの種類名 |

**代表的な `token()` の値:**

| 値 | 意味 | 例 |
|----|------|-----|
| `Keyword` | キーワード | `class`, `public`, `if` |
| `Identifier` | 識別子 | `Task`, `main` |
| `Literal` | リテラル | `42`, `3.14` |
| `String` | 文字列リテラル | `"hello"` |
| `Operator` | 演算子 | `+`, `=`, `==` |
| `Punctuation` | 区切り文字 | `{`, `}`, `;` |
| `Comment` | コメント | `// comment` |
| `Whitespace` | 空白 | (スペース、タブ) |
| `Newline` | 改行 | (改行文字) |

**使用例:**
```java
Lexeme lex = lexer.first();
if ("Identifier".equals(lex.token())) {
    System.out.println("識別子: " + lex.text());
}
```

---

#### `Lexeme next()`

次の Lexeme を返します。
現在の Lexeme がファイルの末尾の場合は `null` を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Lexeme` - 次の Lexeme。末尾の場合は `null` |

**使用例:**
```java
Lexeme lex = lexer.first();
while (lex != null) {
    // ... 処理 ...
    lex = lex.next();
}
```

---

#### `Lexeme previous()`

前の Lexeme を返します。
現在の Lexeme がファイルの先頭の場合は `null` を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Lexeme` - 前の Lexeme。先頭の場合は `null` |

**使用例:**
```java
Lexeme prev = lex.previous();
if (prev != null) {
    System.out.println("直前のトークン: " + prev.text());
}
```

---

#### `Entity entity()`

この Lexeme に関連付けられた Entity を返します。
`Lexer` を `lookupEnts=true` で生成した場合にのみ、識別子トークンに対応する Entity が設定されます。
関連する Entity がない場合（キーワードや演算子など）は `null` を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Entity` - 関連する Entity。なければ `null` |

**使用例:**
```java
Lexeme lex = lexer.first();
while (lex != null) {
    if (lex.entity() != null) {
        System.out.printf("%s → %s%n", lex.text(), lex.entity().longname());
    }
    lex = lex.next();
}
```

---

#### `Reference reference()`

この Lexeme に関連付けられた Reference を返します。
関連する Reference がない場合は `null` を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `Reference` - 関連する Reference。なければ `null` |

**使用例:**
```java
if (lex.reference() != null) {
    Reference ref = lex.reference();
    System.out.printf("参照種別: %s%n", ref.kind().name());
}
```

---

#### `int lineBegin()`

Lexeme の開始行番号を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `int` - 開始行番号 |

**使用例:**
```java
System.out.println("開始行: " + lex.lineBegin());
```

---

#### `int lineEnd()`

Lexeme の終了行番号を返します。
複数行にまたがるトークン（複数行コメントなど）の場合、`lineBegin()` と異なる値になります。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `int` - 終了行番号 |

**使用例:**
```java
if (lex.lineBegin() != lex.lineEnd()) {
    System.out.println("複数行トークン: 行" + lex.lineBegin() + "〜" + lex.lineEnd());
}
```

---

#### `int columnBegin()`

Lexeme の開始列番号（その列を含む）を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `int` - 開始列番号（inclusive） |

**使用例:**
```java
System.out.printf("位置: 行%d 列%d%n", lex.lineBegin(), lex.columnBegin());
```

---

#### `int columnEnd()`

Lexeme の終了列番号（その列を含まない）を返します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `int` - 終了列番号（exclusive） |

**使用例:**
```java
int length = lex.columnEnd() - lex.columnBegin();
System.out.println("トークンの幅: " + length + "文字");
```

---

#### `boolean inactive()`

この Lexeme が非アクティブ（`#ifdef` 等のプリプロセッサ条件で除外された領域）かどうかを返します。
Java では通常使用しません（C/C++ 向け機能）。

| 項目 | 内容 |
|------|------|
| 引数 | なし |
| 戻り値 | `boolean` - 非アクティブの場合は `true` |

**使用例:**
```java
if (!lex.inactive()) {
    // アクティブなコードのみ処理
    System.out.println(lex.text());
}
```

---

## 8. UnderstandException クラス

```
com.scitools.understand.UnderstandException
```

Understand Java API の操作中にエラーが発生した場合にスローされる例外クラスです。
`java.lang.Exception` を継承しています。
`Understand.open()` や `Entity.draw()`、`Entity.lexer()` などのメソッドでスローされる可能性があります。

### コンストラクタ一覧

| コンストラクタ | 説明 |
|--------------|------|
| `UnderstandException()` | デフォルトコンストラクタ |
| `UnderstandException(String arg0)` | メッセージ付きコンストラクタ |
| `UnderstandException(Throwable arg0)` | 原因付きコンストラクタ |
| `UnderstandException(String arg0, Throwable arg1)` | メッセージと原因付きコンストラクタ |

### コンストラクタ詳細

#### `UnderstandException()`

メッセージなしの例外を生成します。

| 項目 | 内容 |
|------|------|
| 引数 | なし |

---

#### `UnderstandException(String arg0)`

指定されたメッセージを持つ例外を生成します。

| 項目 | 内容 |
|------|------|
| 引数 | `arg0` - エラーメッセージ |

---

#### `UnderstandException(Throwable arg0)`

指定された原因を持つ例外を生成します。

| 項目 | 内容 |
|------|------|
| 引数 | `arg0` - 原因となった例外 |

---

#### `UnderstandException(String arg0, Throwable arg1)`

指定されたメッセージと原因を持つ例外を生成します。

| 項目 | 内容 |
|------|------|
| 引数 | `arg0` - エラーメッセージ |
|      | `arg1` - 原因となった例外 |

---

### 例外の主なエラー種別

`Understand.open()` がスローする `UnderstandException` には、以下のエラー種別があります。

| エラー種別 | 説明 | 対処法 |
|-----------|------|--------|
| `DBAlreadyOpen` | 既に別のデータベースが開いている | `db.close()` を呼んでから開き直す |
| `DBCorrupt` | データベースファイルが破損 | データベースを再作成する |
| `DBOldVersion` | データベースが古いバージョン | 現在のバージョンで再ビルドする |
| `DBUnknownVersion` | バージョンを認識できない | Understand を更新して再ビルドする |
| `DBUnableOpen` | ファイルを開けない | ファイルパスとアクセス権を確認する |
| `NoApiLicense` | API ライセンスが無効 | ライセンス設定を確認する |

`Entity.draw()` がスローする `UnderstandException` には、以下のエラー種別があります。

| エラー種別 | 説明 |
|-----------|------|
| `NoFont` | フォントが見つからない |
| `NoImage` | 画像を生成できない |
| `TooBig` | グラフが大きすぎる |
| `UnableCreateFile` | ファイルを作成できない |
| `UnsupportedFile` | 未対応のファイル形式 |

**使用例:**
```java
try {
    Database db = Understand.open("project.udb");
    // ... 処理 ...
    db.close();
} catch (UnderstandException e) {
    System.err.println("Understandエラー: " + e.getMessage());
}
```

---

## 付録: Java向け kindstring フィルタ一覧

kindstring は、`db.ents()`、`entity.refs()`、`kind.check()` などのメソッドに渡すフィルタ文字列です。
カンマ区切りで複数指定、`~` プレフィックスで除外を表します。

### kindstring フィルタ構文

| 構文 | 説明 | 例 |
|------|------|-----|
| 単一指定 | 指定した種別のみ | `"class"` |
| 複数指定（カンマ区切り） | いずれかに該当 | `"class, interface"` |
| 否定（`~` プレフィックス） | 指定した種別を除外 | `"~unknown"` |
| 組み合わせ | 種別指定と除外を同時に | `"class ~unknown ~unresolved"` |

### Entity kindstring（エンティティ種別）

| kindstring | 対象 |
|-----------|------|
| `class` | クラス |
| `interface` | インターフェース |
| `enum` | 列挙型 |
| `annotation` | アノテーション型 |
| `method` | メソッド |
| `variable` | 変数（フィールド、ローカル変数、パラメータ含む） |
| `parameter` | メソッドパラメータ |
| `file` | ソースファイル |
| `package` | パッケージ |
| `~unknown` | 未解決（Unknown）を除外 |
| `~unresolved` | 未解決（Unresolved）を除外 |

### Reference kindstring（参照種別）

| kindstring | 意味 | 逆方向 |
|-----------|------|-------|
| `call` | メソッド呼び出し | `callby` |
| `use` | 変数の使用 | `useby` |
| `set` | 変数への代入 | `setby` |
| `define` | 定義 | `definein` |
| `declare` | 宣言 | `declarein` |
| `extend` | クラス継承 | `extendby` |
| `implement` | インターフェース実装 | `implementby` |
| `import` | インポート | `importby` |
| `create` | オブジェクト生成 | `createby` |
| `typed` | 型の使用 | `typedby` |
| `override` | メソッドオーバーライド | `overrideby` |
| `couple` | 結合 | `coupleby` |

### 修飾子 kindstring

以下の kindstring は Entity kindstring と組み合わせて使用し、アクセス修飾子やその他の修飾子でフィルタリングします。

| kindstring | 意味 |
|-----------|------|
| `public` | public 修飾子を持つ |
| `private` | private 修飾子を持つ |
| `protected` | protected 修飾子を持つ |
| `static` | static 修飾子を持つ |
| `abstract` | abstract 修飾子を持つ |
| `final` | final 修飾子を持つ |

**修飾子の使用例:**

```java
// public メソッドのみ取得
Entity[] publicMethods = db.ents("method public");

// private でない変数を取得
Entity[] nonPrivateVars = db.ents("variable ~private");

// 抽象クラスのみ取得
Entity[] abstractClasses = db.ents("class abstract");

// static final フィールド（定数）のみ取得
Entity[] constants = db.ents("variable static final");
```
