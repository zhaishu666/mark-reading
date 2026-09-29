#!/bin/bash
# bookstats.sh — mark-reading 数据统计器
# 用法: ./bookstats.sh <数据目录>

#参数守卫,只允许输入一个参数
if [ "$#" -ne 1 ] ; then
    echo "用法: $0 <文件目录>"
    exit 1
fi
DATA_DIR="$1"

#文件路径校验
if [ ! -e "$DATA_DIR/books.tsv" ]; then
    echo "错误: 找不到 $DATA_DIR/books.tsv"
    exit 1
fi

read -p "请输入选项: 1=全部书籍 2=统计 3=退出" chose

list_books(){
tail -n +2 "$DATA_DIR"/books.tsv | cut -f2,3 | column -t
}

show_stats(){
tail -n +2 "$DATA_DIR"/excerpts.tsv | cut -f2 | sort | uniq -c
}

case $chose in 
1)
list_books
[ $? -eq 0 ] && echo "完成"
;;
2)
show_stats
[ $? -eq 0 ] && echo "完成"
;;
3)
  echo "再见"
;; 
*)
  echo "非法输入: $chose"
  exit 1
;;
esac 


