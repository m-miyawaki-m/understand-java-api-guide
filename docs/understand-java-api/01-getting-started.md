# 01 - はじめに（Getting Started）

Understand Java API を使うと、SciTools Understand が解析したソースコード情報にプログラムからアクセスできます。
本章では、環境構築から最初のプログラム実行までの手順を解説します。

---

## 前提条件

本ガイドを進めるには、以下のソフトウェアが必要です。

| ソフトウェア | バージョン | 備考 |
|---|---|---|
| SciTools Understand | 6.x 以降 | API ライセンスが有効であること |
| Java (JDK) | 11 以上 | `java -version` で確認 |
| Gradle | 7.x | ビルドツールとして使用 |

---

## Understand.jar の所在

Understand Java API は `Understand.jar` として提供されています。
デフォルトのインストールでは、以下のパスに配置されます。

**Windows:**

```
C:\Program Files\SciTools\bin\pc-win64\Java\Understand.jar
```

**Linux:**

```
/opt/scitools/bin/linux64/Java/Understand.jar
```

**macOS:**

```
/Applications/Understand.app/Contents/MacOS/Java/Understand.jar
```

> **注意:** Understand Java API はネイティブライブラリ（JNI）を使用します。
> 実行時には SciTools の `bin` ディレクトリが `PATH`（Windows）または `LD_LIBRARY_PATH`（Linux）に含まれている必要があります。

---

## Gradle 7 プロジェクトへの組み込み方法

### 方法1: libs ディレクトリにコピーする（推奨）

`Understand.jar` をプロジェクトの `libs/` ディレクトリにコピーし、`build.gradle` で参照します。

```
myproject/
  libs/
    Understand.jar
  src/
    main/
      java/
        BasicUsage.java
  build.gradle
```

`build.gradle` の記述例：

```groovy
plugins {
    id 'java'
    id 'application'
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation files('libs/Understand.jar')
}

application {
    mainClass = 'BasicUsage'
}
```

ビルドと実行：

```bash
# ビルド
./gradlew build

# 実行
./gradlew run --args="sample.udb"
```

### 方法2: 絶対パスで参照する

JAR をコピーせず、インストール先を直接参照することもできます。

```groovy
dependencies {
    implementation files('C:/Program Files/SciTools/bin/pc-win64/Java/Understand.jar')
}
```

> **注意:** 絶対パスを使う場合、チームメンバー間で Understand のインストール先が異なると
> ビルドが失敗します。チーム開発では方法1を推奨します。

---

## java -cp による直接実行方法

Gradle を使わずに、`java` コマンドで直接実行することもできます。

**Windows（コマンドプロンプト）:**

```
javac -cp "C:\Program Files\SciTools\bin\pc-win64\Java\Understand.jar" BasicUsage.java
java -cp "C:\Program Files\SciTools\bin\pc-win64\Java\Understand.jar;." BasicUsage sample.udb
```

**Linux / macOS:**

```bash
javac -cp "/opt/scitools/bin/linux64/Java/Understand.jar" BasicUsage.java
java -cp "/opt/scitools/bin/linux64/Java/Understand.jar:." BasicUsage sample.udb
```

> **ポイント:** Windows ではクラスパスの区切り文字がセミコロン（`;`）、Linux / macOS ではコロン（`:`）です。

---

## Understand データベース（.udb）の作成手順

Understand Java API を使うには、あらかじめ Understand でソースコードを解析し、データベース（`.udb` ファイル）を作成しておく必要があります。

### GUI から作成する

1. Understand を起動する
2. **File** → **New** → **Project** を選択
3. プロジェクト名と保存先を指定
4. 解析対象のソースファイルまたはディレクトリを追加
5. 解析言語（Java 等）を確認
6. **Analyze** ボタンで解析を実行

### und コマンドで作成する

コマンドラインツール `und` を使えば、GUI を開かずにデータベースを作成できます。

```bash
und create -db sample.udb -languages java add ./src analyze
```

このコマンドは以下を一括で実行します：

1. `sample.udb` を新規作成
2. 解析言語を Java に設定
3. `./src` ディレクトリ配下のソースファイルを追加
4. 解析を実行

---

## 最初のプログラム

`BasicUsage.java`（[ソースコード全文](samples/BasicUsage.java)）を使って、API の基本操作を確認します。

### データベースを開く

```java
db = Understand.open(dbPath);
System.out.println("データベースを開きました: " + db.name());
```

`Understand.open()` に `.udb` ファイルのパスを渡すと、`Database` オブジェクト（データベース）が返されます。
`db.name()` でデータベースのファイルパスを取得できます。

### 対応言語を表示する

```java
String[] languages = db.language();
System.out.println("言語: " + String.join(", ", languages));
```

`db.language()` は、データベースに登録されている解析対象言語の一覧を文字列配列で返します。

### エンティティ（Entity）を取得する

エンティティとは、ソースコード中のクラス、メソッド、変数などの構成要素を表すオブジェクトです。

```java
// 全エンティティを取得
Entity[] allEntities = db.ents(null);
System.out.println("エンティティ総数: " + allEntities.length);
```

`db.ents(null)` は全てのエンティティを返します。引数にフィルタ文字列を指定すると、特定の種別（Kind）のみ取得できます。

```java
// クラスエンティティのみ取得
Entity[] classes = db.ents("class");
for (Entity cls : classes) {
    System.out.printf("  %s (種別: %s)%n", cls.longname(), cls.kind().name());
}
```

- `cls.longname()` — 完全修飾名（例: `sample.Task`）
- `cls.kind().name()` — エンティティの種別名（例: `Java Class`）

### データベースを閉じる

```java
finally {
    if (db != null) {
        db.close();
    }
}
```

`Database` オブジェクトは使い終わったら必ず `close()` で閉じてください。
`try-finally` パターンを使うことで、例外発生時でも確実にリソースを解放できます。

---

## 実行例と期待出力

`SampleProject.java` を Understand で解析して作成した `sample.udb` に対して実行すると、以下のような出力が得られます。

```
データベースを開きました: /path/to/sample.udb
言語: Java
エンティティ総数: 42

--- クラス一覧 ---
  sample.BaseItem (種別: Java Abstract Class)
  sample.Task (種別: Java Class)
  sample.TaskManager (種別: Java Class)
  sample.SampleProject (種別: Java Class)
  sample.Task.Priority (種別: Java Enum)

--- メソッド一覧 ---
  sample.BaseItem.BaseItem (種別: Java Method Constructor)
  sample.BaseItem.getId (種別: Java Method)
  sample.BaseItem.getName (種別: Java Method)
  sample.BaseItem.toDisplayString (種別: Java Method)
  sample.BaseItem.isValid (種別: Java Abstract Method)
  sample.Task.Task (種別: Java Method Constructor)
  sample.Task.getPriority (種別: Java Method)
  sample.Task.setPriority (種別: Java Method)
  sample.Task.isCompleted (種別: Java Method)
  sample.Task.complete (種別: Java Method)
  sample.Task.isValid (種別: Java Method)
  sample.Task.toDisplayString (種別: Java Method)
  sample.TaskManager.addTask (種別: Java Method)
  sample.TaskManager.findById (種別: Java Method)
  sample.TaskManager.getByPriority (種別: Java Method)
  sample.TaskManager.completeTask (種別: Java Method)
  sample.TaskManager.printAll (種別: Java Method)
  sample.TaskManager.countCompleted (種別: Java Method)
  sample.SampleProject.main (種別: Java Static Method)

データベースを閉じました。
```

> **注意:** エンティティ総数や種別名は Understand のバージョンや設定によって異なる場合があります。

---

## よくあるエラーと対処

`Understand.open()` は問題が発生すると `UnderstandException` をスローします。
以下に代表的なエラーとその対処法をまとめます。

| エラー | 原因 | 対処法 |
|---|---|---|
| `DBAlreadyOpen` | 既に別のデータベースを開いている | 先に `db.close()` を呼んでから新しいデータベースを開く |
| `DBCorrupt` | データベースファイルが破損している | `und` コマンドまたは GUI でデータベースを再作成する |
| `DBOldVersion` | データベースが古いバージョンで作成された | 現在のバージョンの Understand でデータベースを再ビルドする |
| `DBUnknownVersion` | データベースのバージョンが認識できない | Understand を最新バージョンに更新し、データベースを再ビルドする |
| `DBUnableOpen` | ファイルが見つからない、またはアクセスできない | ファイルパスが正しいか確認する。相対パスの場合はカレントディレクトリに注意 |
| `NoApiLicense` | API ライセンスが有効でない | Understand のライセンス設定を確認する。API アクセスには対応するライセンスが必要 |
| `UnsatisfiedLinkError` | ネイティブライブラリ（JNI）が見つからない | `PATH`（Windows）または `LD_LIBRARY_PATH`（Linux）に SciTools の `bin` ディレクトリを追加する |

> **ヒント:** `UnsatisfiedLinkError` は `UnderstandException` ではなく Java の `Error` です。
> このエラーが発生する場合は、環境変数の設定を見直してください。
>
> **Windows の場合:**
> ```
> set PATH=%PATH%;C:\Program Files\SciTools\bin\pc-win64
> ```
>
> **Linux の場合:**
> ```bash
> export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/opt/scitools/bin/linux64
> ```

---

次章: [02 - 主要概念（Core Concepts）](02-core-concepts.md)
