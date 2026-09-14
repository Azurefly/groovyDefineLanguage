#!/bin/bash
set -e

echo "=== GDL 引擎代码推送到远程仓库 ==="
if [ ! -d ".git" ]; then
    echo "正在恢复本地 Git 仓库配置..."
    git clone gdl.bundle .git_temp
    mv .git_temp/.git .git
    rm -rf .git_temp
fi

git remote set-url origin https://github.com/Azurefly/groovyDefineLanguage.git
echo "正在推送到 GitHub: https://github.com/Azurefly/groovyDefineLanguage.git ..."
git push -u origin main

echo "=== 推送成功！==="
