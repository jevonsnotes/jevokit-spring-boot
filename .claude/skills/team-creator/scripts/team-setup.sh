#!/usr/bin/env bash
# team-setup.sh — OpenSpec 检测 / 全局安装 / 项目初始化
#
# 用法:
#   bash team-setup.sh [PROJECT_DIR]
#
#   PROJECT_DIR  要执行 openspec init 的项目目录，默认当前目录。
#
# 退出码:
#   0  OpenSpec 可用且项目已初始化（本来就装好或本次装好都算）
#   1  安装失败
#   2  init 失败
#
# 输出为结构化行（KEY: value），供技能解析。

set -u

PROJECT_DIR="${1:-.}"
PKG="@fission-ai/openspec@latest"

emit() { printf '%s: %s\n' "$1" "$2"; }

emit "PROJECT_DIR" "$PROJECT_DIR"

# ---------- 1. 检测全局是否已安装 ----------
if command -v openspec >/dev/null 2>&1; then
  VER="$(openspec --version 2>/dev/null || echo unknown)"
  emit "STATUS" "installed-global"
  emit "VERSION" "$VER"
else
  emit "STATUS" "missing"
  # ---------- 2. 缺失则全局安装 ----------
  emit "ACTION" "install-global"
  if ! npm install -g "$PKG"; then
    emit "RESULT" "install-failed"
    exit 1
  fi
  # 安装后重新确认
  if ! command -v openspec >/dev/null 2>&1; then
    emit "RESULT" "install-failed-not-on-path"
    emit "HINT" "全局 npm bin 目录可能不在 PATH，请检查 npm bin -g 输出"
    exit 1
  fi
  VER="$(openspec --version 2>/dev/null || echo unknown)"
  emit "VERSION" "$VER"
  emit "RESULT" "installed-ok"
fi

# ---------- 3. 进入项目目录，若尚未初始化则 init ----------
cd "$PROJECT_DIR" || { emit "RESULT" "cd-failed"; exit 2; }

# 判断是否已初始化：openspec 项目通常含 openspec/ 目录或 openspec.config.* 文件
if [ -d "openspec" ] || ls openspec.config.* >/dev/null 2>&1; then
  emit "INIT_STATUS" "already-initialized"
else
  emit "INIT_STATUS" "not-initialized"
  emit "ACTION" "openspec-init"
  # init 可能是交互式的；传入默认选项。若 openspec init 支持非交互标志优先使用。
  if openspec init </dev/null; then
    emit "INIT_RESULT" "init-ok"
  else
    emit "INIT_RESULT" "init-failed"
    exit 2
  fi
fi

emit "DONE" "ok"
exit 0
