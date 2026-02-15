# Project: understand-java-api-guide

## Overview
SciTools Understand Java API の日本語ドキュメント・サンプルコード集

## Tech Stack
- Java 11+
- Gradle 7
- SciTools Understand Java API (`com.scitools.understand`)

## Common Commands
- Build: `./gradlew build`
- Run sample: `./gradlew run --args="<UDBパス> [コマンド] [引数]"`

## Code Style
- ドキュメント本文: 日本語
- サンプルコードのコメント: 日本語
- クラス名・メソッド名: 原文（英語）のまま
- 技術用語（Entity, Reference, Kind等）: 初出時に日本語訳を併記、以降は英語で統一
- CSV出力等のI/OはJava標準ライブラリのみ使用（外部ライブラリ不可）

## Workflow
1. Read existing code before making changes
2. Use Conventional Commits format

## Directory Structure
- `docs/understand-java-api/` - 日本語ドキュメント（01〜05）
- `docs/understand-java-api/samples/` - 実行可能なJavaサンプルコード
- `docs/plans/` - 設計書・実装計画
- `templates/` - Claude Code プロジェクトテンプレート
