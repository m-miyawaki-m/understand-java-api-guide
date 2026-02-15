# Understand Java API 日本語ドキュメント 設計書

## 概要

SciTools Understand の Java API (`com.scitools.understand`) の日本語リファレンス、サンプルコード、Javaプロジェクトへの組み込み方法をまとめたドキュメントを作成する。

## 背景

- `C:\Program Files\SciTools\doc\manuals\java\` に英語のJavadocリファレンスが存在
- 社内開発者・新人学習者向けに日本語ドキュメントが必要
- 実行環境は Understand 6 の JAR (`Understand.jar`)
- 分析対象言語は Java
- ビルドシステム: Gradle 7
- CSV出力等のI/OはJava標準ライブラリのみ使用

## 対象読者

1. **新人・学習者**: Understand APIに初めて触れる人（チュートリアルから段階的に学習）
2. **社内開発者**: APIを使ったツール開発・自動化を行う実践者
3. **リファレンス利用者**: メソッド仕様を辞書的に参照する人

## アプローチ

**段階的ガイド＋リファレンス統合型** を採用。チュートリアル → ユースケース別ガイド → APIリファレンスと段階的に構成し、幅広い読者層をカバーする。

## プロジェクト構成

```
understand-java-api-guide/          # claude-project-sample をコピーして作成
├── docs/
│   └── understand-java-api/
│       ├── 01-getting-started.md        # 環境構築・プロジェクト組み込み
│       ├── 02-core-concepts.md          # 主要概念の解説
│       ├── 03-code-exploration.md       # コード構造探索ガイド
│       ├── 04-dependency-analysis.md    # 依存関係分析ガイド
│       ├── 05-api-reference.md          # 全クラス・メソッド日本語リファレンス
│       └── samples/
│           ├── SampleProject.java       # 分析対象のサンプルコード
│           ├── BasicUsage.java          # DB接続・エンティティ取得の基本
│           ├── CodeExplorer.java        # コード構造探索の実装例
│           └── DependencyAnalyzer.java  # 依存関係分析の実装例
```

## 各ドキュメント詳細

### 01-getting-started.md（環境構築・プロジェクト組み込み）

- 前提条件（SciTools Understand インストール済み、Java 11以上）
- `Understand.jar` の所在パス
- Maven/Gradleプロジェクトへの組み込み方法（ローカルJAR依存追加、クラスパス設定）
- Understandデータベース（`.udb`）の作成手順（GUI or `und`コマンド）
- 最初のプログラム：DB接続 → エンティティ一覧表示 → DB切断
- 実行方法（`java -cp` でのJAR指定含む）
- よくあるエラーと対処（ライセンス、DB未作成、パス間違い等）

### 02-core-concepts.md（主要概念の解説）

- Understand APIのオブジェクトモデル概念図
  - `Understand` → `Database` → `Entity` → `Reference` / `Kind`
  - `Entity` → `Lexer` → `Lexeme`
- 各概念の日本語解説
  - **Entity（エンティティ）**: ソースコード上の名前付き要素
  - **Reference（参照）**: エンティティ間の関係
  - **Kind（種別）**: エンティティや参照の分類、kindstringフィルタリング
  - **Lexer/Lexeme（字句解析）**: ソースファイルのトークン単位の解析
- kindstringの書き方と代表的なフィルタ例（Java向け）
- リソース管理の注意点（`Database.close()`必須、1DB制約）

### 03-code-exploration.md（コード構造探索ガイド）

- ユースケース1: プロジェクト内の全クラス一覧を取得する
- ユースケース2: 特定クラスのメソッド一覧を取得する
- ユースケース3: メソッドの呼び出し関係を調べる
- ユースケース4: 変数の参照箇所を特定する
- ユースケース5: ソースコードの字句解析（Lexer/Lexeme）
- 各ユースケースに実行可能なサンプルコード＋出力例付き

### 04-dependency-analysis.md（依存関係分析ガイド）

- ユースケース1: ファイル間の依存関係を取得する（`depends()` / `dependsby()`）
- ユースケース2: クラス間の依存関係を取得する
- ユースケース3: 依存関係をCSV/テキストで出力する
- ユースケース4: グラフ画像の生成（`Entity.draw()` でSVG/PNG出力）
- 各ユースケースに実行可能なサンプルコード＋出力例付き

### 05-api-reference.md（日本語APIリファレンス）

- クラスごとにセクション分け（8クラス）
- 各メソッドについて: シグネチャ、日本語説明、引数・戻り値、例外、簡潔な使用例
- kindstringフィルタの主要一覧表（Java向け）

## サンプルコード設計

### samples/SampleProject.java（分析対象）

分析対象となる小規模Javaコード。以下を含む:
- インターフェース（1つ）
- 基底クラス（1つ）
- 派生クラス（2つ）
- クラス間の依存（import, メソッド呼び出し, フィールド参照）

### samples/BasicUsage.java

01-getting-started.md 対応。DB接続、エンティティ一覧取得、リソース解放の基本パターン。

### samples/CodeExplorer.java

03-code-exploration.md 対応。クラス一覧、メソッド一覧、呼び出し関係探索、変数参照、字句解析を各メソッドに分けて実装。

### samples/DependencyAnalyzer.java

04-dependency-analysis.md 対応。ファイル/クラス間依存関係取得、CSV出力、グラフ画像生成を各メソッドに分けて実装。

### サンプルコード共通方針

- 全サンプルは `main()` を持ち単体実行可能
- UDBファイルのパスはコマンドライン引数で受け取る
- エラーハンドリングは実用的だが最小限
- コード内コメントは日本語
- 「分析対象コード」と「API実行コード」を明確に分離

## 言語方針

- ドキュメント本文: 日本語
- サンプルコードのコメント: 日本語
- クラス名・メソッド名: 原文（英語）のまま
- 技術用語（Entity, Reference, Kind等）: 初出時に日本語訳を併記、以降は英語で統一

## v6/v7 対応方針

- Java APIのJavadocにバージョン注記がないため、全機能をそのまま記載
- 動作確認は Understand 6 の JAR で行う前提で記述
- v6で動作しないメソッドがあった場合は後から注記を追加

## API構成（参考）

`com.scitools.understand` パッケージ: 7クラス + 1例外クラス

| クラス | 役割 |
|--------|------|
| `Understand` | エントリポイント。`open()` でDB接続 |
| `Database` | データベース操作。エンティティ取得、メトリクス |
| `Entity` | コードエンティティ。参照取得、字句解析、依存関係等 |
| `Reference` | エンティティ間の参照関係 |
| `Kind` | エンティティ/参照の種別分類 |
| `Lexer` | 字句解析器 |
| `Lexeme` | 字句トークン |
| `UnderstandException` | API例外 |

## スコープ外

- Python API 関連のドキュメント
- CI/CD 連携ガイド
- Lexer/Lexeme以外の高度な機能（Graph, Arch等はJava APIに存在しない）
