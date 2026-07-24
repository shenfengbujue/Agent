"""
知识库导入脚本
从 Ai-Learn-master 和 free-programming-books-zh_CN-main 导入知识条目到 MySQL
"""
import os
import re
import pymysql
from datetime import datetime

# MySQL 配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'eduagent',
    'charset': 'utf8mb4'
}

REPO_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data', 'repos')
AI_LEARN_PATH = os.path.join(REPO_PATH, 'Ai-Learn-master')
FREE_BOOKS_PATH = os.path.join(REPO_PATH, 'free-programming-books-zh_CN-main')

# 目录名关键字 → knowledge_base domain
DOMAIN_KEYWORDS = {
    'PYTHON': ['Python', 'python', 'NLP', 'BERT', '数据分析', '机器学习',
               '数据挖掘', '深度学习', '计算机视觉', 'Keras', 'PyTorch',
               'Tensorflow', 'Opencv', 'MaskRcnn', '自然语言处理'],
    'GENERAL': ['数学'],
}

def classify_domain(dir_name, title):
    """根据目录名和标题分类到知识库"""
    for domain, keywords in DOMAIN_KEYWORDS.items():
        for kw in keywords:
            if kw in dir_name or kw in title:
                return domain
    return 'GENERAL'

def classify_category(dir_name):
    """根据目录名映射到分类"""
    category_map = {
        '数学': '数学基础', 'Python': 'Python编程', '数据分析': '数据分析',
        '机器学习': '机器学习', '深度学习': '深度学习', '计算机视觉': '计算机视觉',
        '自然语言处理': '自然语言处理', '数据挖掘': '数据挖掘', 'NLP': '自然语言处理',
        'BERT': '自然语言处理', 'Keras': '深度学习', 'PyTorch': '深度学习',
        'Tensorflow': '深度学习', 'Opencv': '计算机视觉', 'MaskRcnn': '计算机视觉',
    }
    for kw, cat in category_map.items():
        if kw in dir_name:
            return cat
    return '编程学习'

def parse_readme_books(readme_path):
    """解析 README.md 中的书籍链接列表"""
    books = []
    if not os.path.exists(readme_path):
        return books
    
    with open(readme_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    lines = content.split('\n')
    for line in lines:
        line = line.strip()
        # 匹配 Markdown 链接格式: * [标题](URL)
        if line.startswith('* [') and '](' in line:
            try:
                title_start = line.index('[') + 1
                title_end = line.index('](')
                title = line[title_start:title_end]
                
                url_start = title_end + 2
                url_end = line.index(')', url_start)
                url = line[url_start:url_end]
                
                if title and url:
                    books.append((title, url))
            except (ValueError, IndexError):
                continue
    return books

def main():
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()
    
    try:
        # 1. 获取 knowledge_base 映射
        cursor.execute("SELECT id, domain FROM knowledge_base")
        base_map = {row[1]: row[0] for row in cursor.fetchall()}
        print(f"知识库映射: {base_map}")
        
        total = 0
        
        # 2. 导入 Ai-Learn 仓库中的 .md 文件
        if os.path.exists(AI_LEARN_PATH):
            print(f"\n开始导入 Ai-Learn 知识库...")
            for root, dirs, files in os.walk(AI_LEARN_PATH):
                for file in files:
                    if not (file.endswith('.md') or file.endswith('.txt')):
                        continue
                    
                    file_path = os.path.join(root, file)
                    try:
                        with open(file_path, 'r', encoding='utf-8') as f:
                            content = f.read()
                    except Exception:
                        continue
                    
                    if not content.strip():
                        continue
                    
                    title = file.replace('.md', '').replace('.txt', '').strip()
                    parent_dir = os.path.basename(root)
                    domain = classify_domain(parent_dir, title)
                    category = classify_category(parent_dir)
                    base_id = base_map.get(domain, base_map.get('GENERAL', 1))
                    
                    # 截取内容（最多2000字符）
                    excerpt = content[:2000] if len(content) > 2000 else content
                    
                    sql = """INSERT INTO knowledge_entry 
                             (base_id, title, content, category, sub_module, created_at, updated_at)
                             VALUES (%s, %s, %s, %s, %s, %s, %s)"""
                    cursor.execute(sql, (base_id, title, excerpt, category, parent_dir,
                                        datetime.now(), datetime.now()))
                    total += 1
                    
                    if total % 20 == 0:
                        conn.commit()
                        print(f"  已导入 {total} 条...")
            
            conn.commit()
            print(f"  Ai-Learn 导入完成: {total} 条")
        else:
            print(f"  Ai-Learn 路径不存在: {AI_LEARN_PATH}")
        
        # 3. 导入 free-programming-books 中的书籍列表
        books_count = 0
        readme = os.path.join(FREE_BOOKS_PATH, 'README.md')
        if os.path.exists(readme):
            print(f"\n开始导入免费书籍列表...")
            books = parse_readme_books(readme)
            base_id = base_map.get('GENERAL', 1)
            
            for title, url in books:
                sql = """INSERT INTO knowledge_entry 
                         (base_id, title, content, category, sub_module, metadata, created_at, updated_at)
                         VALUES (%s, %s, %s, %s, %s, %s, %s, %s)"""
                cursor.execute(sql, (
                    base_id,
                    title[:200],
                    f"免费中文计算机书籍: {url}",
                    '书籍资源',
                    '免费书籍',
                    '{"url":"' + url + '","source":"free-programming-books-zh_CN"}',
                    datetime.now(), datetime.now()
                ))
                books_count += 1
                total += 1
                
                if books_count % 100 == 0:
                    conn.commit()
                    print(f"  已导入书籍 {books_count} 条...")
            
            conn.commit()
            print(f"  书籍导入完成: {books_count} 条")
        
        print(f"\n========================")
        print(f"导入完成! 总计: {total} 条知识条目")
        print(f"========================")
        
    except Exception as e:
        conn.rollback()
        print(f"导入失败: {e}")
        raise
    finally:
        cursor.close()
        conn.close()

if __name__ == '__main__':
    main()