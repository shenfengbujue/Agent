#!/usr/bin/env python3
"""
知识库数据导入脚本 - 整合三种数据源：
1. HuggingFace chinese-fineweb-edu-v2 数据集
2. 中文维基百科转储
3. GitHub 中文教材仓库
"""

import os
import json
import re
import hashlib
import subprocess
from pathlib import Path
from typing import List, Dict, Any, Optional
from datetime import datetime

# 设置国内镜像
os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"

# 输出路径
OUTPUT_PATH = Path(__file__).parent.parent / "backend" / "data" / "knowledge_base.json"

# 分类映射
CATEGORY_MAPPING = {
    "python": "python",
    "编程": "python",
    "编程开发": "python",
    "ai": "ai",
    "人工智能": "ai",
    "机器学习": "ai",
    "深度学习": "ai",
    "math": "math",
    "数学": "math",
    "微积分": "math",
    "线性代数": "math",
    "概率论": "math",
    "english": "english",
    "英语": "english",
    "商务英语": "english",
    "design": "design",
    "设计": "design",
    "ui": "design",
    "ux": "design",
    "business": "business",
    "商业": "business",
    "商业分析": "business",
}

# 难度映射
DIFFICULTY_MAPPING = {
    "入门": "入门",
    "基础": "基础",
    "初级": "入门",
    "中级": "中级",
    "进阶": "中级",
    "高级": "高级",
    "深入": "高级",
}

# 类型映射
TYPE_MAPPING = {
    "文章": "文章",
    "article": "文章",
    "视频": "视频",
    "video": "视频",
    "教程": "文章",
    "课程": "视频",
    "练习": "练习",
    "exercise": "练习",
    "测验": "测验",
    "test": "测验",
}

# ID计数器
_id_counter = 1


def get_next_id() -> int:
    """获取下一个ID"""
    global _id_counter
    id = _id_counter
    _id_counter += 1
    return id


def detect_category(text: str, title: str = "") -> str:
    """根据文本内容检测分类"""
    combined = (title + " " + text).lower()
    
    # 按优先级检测
    if any(kw in combined for kw in ["python", "编程", "代码", "函数", "变量", "类", "对象", 
                                       "算法", "数据结构", "numpy", "pandas", "django", "flask",
                                       "vue", "react", "javascript", "html", "css", "前端", "后端",
                                       "api", "数据库", "sql", "mysql", "linux", "git", "github",
                                       "软件工程", "devops", "测试", "敏捷", "docker", "kubernetes",
                                       "数据科学", "数据分析", "爬虫", "web开发", "移动开发",
                                       "android", "ios", "flutter", "swift", "kotlin", "java", "c++", "go", "rust"]):
        return "python"
    
    if any(kw in combined for kw in ["ai", "人工智能", "机器学习", "深度学习", "神经网络", 
                                       "nlp", "自然语言处理", "计算机视觉", "图像识别",
                                       "tensorflow", "pytorch", "keras", "transformer", "bert",
                                       "gpt", "llm", "大模型", "chatgpt", "强化学习", "监督学习",
                                       "无监督学习", "聚类", "分类", "回归", "预测", "模型训练",
                                       "特征工程", "数据挖掘", "推荐系统", "知识图谱"]):
        return "ai"
    
    if any(kw in combined for kw in ["数学", "微积分", "线性代数", "概率论", "统计", 
                                       "矩阵", "向量", "导数", "积分", "极限", "方程",
                                       "函数", "几何", "代数", "数论", "拓扑", "离散数学",
                                       "优化", "凸优化", "数值计算", "随机过程", "贝叶斯"]):
        return "math"
    
    if any(kw in combined for kw in ["英语", "english", "商务英语", "口语", "听力", 
                                       "阅读", "写作", "翻译", "词汇", "语法", "发音",
                                       "雅思", "托福", "gre", "gmat", "四六级", "考研英语",
                                       "商务邮件", "会议用语", "谈判"]):
        return "english"
    
    if any(kw in combined for kw in ["设计", "ui", "ux", "用户体验", "界面设计", 
                                       "视觉设计", "交互设计", "原型", "figma", "sketch",
                                       "色彩", "排版", "布局", "图标", "品牌", "平面设计",
                                       "动效", "动画", "插画", "产品设计", "工业设计"]):
        return "design"
    
    if any(kw in combined for kw in ["商业", "business", "商业分析", "市场", "营销", 
                                       "战略", "管理", "运营", "财务", "投资", "创业",
                                       "商业模式", "swot", "pest", "竞品分析", "用户研究",
                                       "数据分析", "报表", "决策", "领导力", "项目管理"]):
        return "business"
    
    return "other"


def detect_difficulty(text: str, title: str = "") -> str:
    """检测难度级别"""
    combined = (title + " " + text).lower()
    
    if any(kw in combined for kw in ["入门", "基础", "初级", "入门教程", "零基础", 
                                       "初学者", "从零开始", "快速入门", "入门指南", "基础教程"]):
        return "入门"
    
    if any(kw in combined for kw in ["中级", "进阶", "中级教程", "进阶教程", 
                                       "深入", "实战", "应用", "案例", "项目"]):
        return "中级"
    
    if any(kw in combined for kw in ["高级", "深入", "高级教程", "精通", 
                                       "专家", "专业", "高级应用", "高级技巧", "架构"]):
        return "高级"
    
    # 根据内容复杂度判断
    if len(combined) > 5000:
        return "中级"
    elif len(combined) > 10000:
        return "高级"
    
    return "基础"


def detect_type(text: str, title: str = "") -> str:
    """检测资源类型"""
    combined = (title + " " + text).lower()
    
    if any(kw in combined for kw in ["视频", "video", "课程", "教程视频", 
                                       "讲座", "直播", "录播", "微课"]):
        return "视频"
    
    if any(kw in combined for kw in ["练习", "exercise", "习题", "作业", 
                                       "实践", "动手", "实验", "上机"]):
        return "练习"
    
    if any(kw in combined for kw in ["测验", "test", "考试", "测试", 
                                       "题库", "真题", "模拟题"]):
        return "测验"
    
    return "文章"


def estimate_duration(text: str) -> str:
    """估算学习时长"""
    word_count = len(text)
    
    # 文章类型：按字数估算阅读时间
    if word_count < 1000:
        return "5分钟"
    elif word_count < 3000:
        return "15分钟"
    elif word_count < 5000:
        return "30分钟"
    elif word_count < 10000:
        return "45分钟"
    elif word_count < 20000:
        return "60分钟"
    else:
        return "90分钟"


def clean_text(text: str) -> str:
    """清理文本内容"""
    # 移除多余的空白
    text = re.sub(r'\s+', ' ', text)
    # 移除HTML标签
    text = re.sub(r'<[^>]+>', '', text)
    # 移除特殊字符
    text = re.sub(r'[^\w\s\u4e00-\u9fff.,!?;:\-()（）。，！？；：]', '', text)
    return text.strip()


def truncate_content(text: str, max_length: int = 500) -> str:
    """截取内容摘要"""
    if len(text) <= max_length:
        return text
    return text[:max_length] + "..."


def create_knowledge_item(title: str, content: str, source: str, 
                          category: Optional[str] = None,
                          difficulty: Optional[str] = None,
                          type: Optional[str] = None) -> Dict[str, Any]:
    """创建知识库条目"""
    cleaned_content = clean_text(content)
    
    # 自动检测分类
    if not category:
        category = detect_category(cleaned_content, title)
    
    # 自动检测难度
    if not difficulty:
        difficulty = detect_difficulty(cleaned_content, title)
    
    # 自动检测类型
    if not type:
        type = detect_type(cleaned_content, title)
    
    # 估算时长
    duration = estimate_duration(cleaned_content)
    
    # 创建sections（将内容分段）
    sections = []
    paragraphs = cleaned_content.split('\n\n')
    for i, para in enumerate(paragraphs[:5]):  # 只取前5段
        if len(para.strip()) > 50:
            sections.append({
                "title": f"第{i+1}部分",
                "content": para.strip(),
                "startTime": i * 300,
                "videoUrl": "",
                "materials": ""
            })
    
    return {
        "id": get_next_id(),
        "title": title,
        "content": truncate_content(cleaned_content, 500),
        "sections": sections,
        "category": CATEGORY_MAPPING.get(category, category) if category else "other",
        "source": source,
        "difficulty": DIFFICULTY_MAPPING.get(difficulty, difficulty) if difficulty else "基础",
        "type": TYPE_MAPPING.get(type, type) if type else "文章",
        "duration": duration
    }


# ==================== 方案一：HuggingFace数据集 ====================

def load_from_huggingface(max_items: int = 100) -> List[Dict[str, Any]]:
    """从HuggingFace加载chinese-fineweb-edu-v2数据集"""
    print("正在从HuggingFace加载数据集...")
    
    try:
        from datasets import load_dataset
        
        # 使用国内镜像
        ds = load_dataset("opencsg/chinese-fineweb-edu-v2", split="train", streaming=True)
        
        items = []
        for i, item in enumerate(ds):
            if i >= max_items:
                break
            
            text = item.get("text", "")
            if len(text) < 100:  # 过滤太短的内容
                continue
            
            # 尝试提取标题（通常在第一行）
            lines = text.split('\n')
            title = lines[0][:100] if lines else f"教育资料_{i+1}"
            
            knowledge_item = create_knowledge_item(
                title=title,
                content=text,
                source="HuggingFace chinese-fineweb-edu-v2"
            )
            items.append(knowledge_item)
            
            if (i + 1) % 10 == 0:
                print(f"已处理 {i + 1} 条HuggingFace数据...")
        
        print(f"成功加载 {len(items)} 条HuggingFace数据")
        return items
        
    except ImportError:
        print("警告: datasets库未安装，跳过HuggingFace数据源")
        print("请运行: pip install datasets")
        return []
    except Exception as e:
        print(f"加载HuggingFace数据失败: {e}")
        return []


# ==================== 方案二：中文维基百科 ====================

def load_from_wikipedia(wiki_dir: Optional[str] = None, max_items: int = 100) -> List[Dict[str, Any]]:
    """从维基百科JSON文件加载数据"""
    print("正在加载维基百科数据...")
    
    # 默认路径
    if not wiki_dir:
        wiki_dir = Path(__file__).parent / "wiki_output"
    else:
        wiki_dir = Path(wiki_dir)
    
    if not wiki_dir.exists():
        print(f"警告: 维基百科数据目录不存在: {wiki_dir}")
        print("请先下载并处理维基百科数据:")
        print("1. wget https://dumps.wikimedia.org/zhwiki/latest/zhwiki-latest-pages-articles.xml.bz2")
        print("2. pip install wikiextractor")
        print("3. python -m wikiextractor.WikiExtractor zhwiki-latest-pages-articles.xml.bz2 --json -o ./wiki_output --processes 4")
        return []
    
    items = []
    json_files = list(wiki_dir.glob("**/*.json"))
    
    if not json_files:
        print("警告: 未找到维基百科JSON文件")
        return []
    
    print(f"找到 {len(json_files)} 个JSON文件")
    
    processed = 0
    for json_file in json_files:
        try:
            with open(json_file, 'r', encoding='utf-8') as f:
                for line in f:
                    if processed >= max_items:
                        break
                    
                    try:
                        data = json.loads(line)
                        title = data.get("title", "")
                        text = data.get("text", "")
                        
                        if len(text) < 100:  # 过滤太短的内容
                            continue
                        
                        # 过滤非教育相关条目
                        category = detect_category(text, title)
                        if category == "other":
                            continue
                        
                        knowledge_item = create_knowledge_item(
                            title=title,
                            content=text,
                            source="中文维基百科"
                        )
                        items.append(knowledge_item)
                        processed += 1
                        
                        if processed % 10 == 0:
                            print(f"已处理 {processed} 条维基百科数据...")
                            
                    except json.JSONDecodeError:
                        continue
                        
        except Exception as e:
            print(f"读取文件 {json_file} 失败: {e}")
            continue
    
    print(f"成功加载 {len(items)} 条维基百科数据")
    return items


# ==================== 方案三：GitHub教材仓库 ====================

def load_from_github(max_items: int = 100) -> List[Dict[str, Any]]:
    """从GitHub克隆教材仓库并处理"""
    print("正在从GitHub加载教材数据...")
    
    # GitHub仓库列表
    repos = [
        "https://github.com/tangyudi/Ai-Learn",
        "https://github.com/justjavac/free-programming-books-zh_CN"
    ]
    
    clone_dir = Path(__file__).parent / "github_repos"
    clone_dir.mkdir(exist_ok=True)
    
    items = []
    
    for repo_url in repos:
        repo_name = repo_url.split("/")[-1]
        repo_path = clone_dir / repo_name
        
        # 克隆仓库（如果不存在）
        if not repo_path.exists():
            print(f"正在克隆仓库: {repo_url}")
            try:
                subprocess.run(
                    ["git", "clone", "--depth", "1", repo_url, str(repo_path)],
                    check=True,
                    capture_output=True,
                    text=True
                )
                print(f"成功克隆: {repo_name}")
            except subprocess.CalledProcessError as e:
                print(f"克隆失败: {repo_name}, {e.stderr}")
                continue
            except FileNotFoundError:
                print("警告: git未安装，跳过GitHub数据源")
                return []
        
        # 处理Markdown文件
        md_files = list(repo_path.glob("**/*.md"))
        print(f"在 {repo_name} 中找到 {len(md_files)} 个Markdown文件")
        
        processed = 0
        for md_file in md_files:
            if len(items) >= max_items:
                break
            
            try:
                with open(md_file, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                if len(content) < 200:  # 过滤太短的文件
                    continue
                
                # 提取标题（从文件名或第一个标题）
                title = md_file.stem
                title_match = re.search(r'^#\s+(.+)', content)
                if title_match:
                    title = title_match.group(1)
                
                knowledge_item = create_knowledge_item(
                    title=title,
                    content=content,
                    source=f"GitHub: {repo_name}"
                )
                items.append(knowledge_item)
                processed += 1
                
                if processed % 10 == 0:
                    print(f"已处理 {processed} 个Markdown文件...")
                    
            except Exception as e:
                print(f"读取文件 {md_file} 失败: {e}")
                continue
    
    print(f"成功加载 {len(items)} 条GitHub教材数据")
    return items


# ==================== 整合数据 ====================

def save_knowledge_base(items: List[Dict[str, Any]], output_path: Optional[Path] = None):
    """保存知识库到JSON文件"""
    if not output_path:
        output_path = OUTPUT_PATH
    
    # 确保输出目录存在
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    # 保存JSON
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(items, f, ensure_ascii=False, indent=2)
    
    print(f"知识库已保存到: {output_path}")
    print(f"总计 {len(items)} 条数据")


def main():
    """主函数：整合三种数据源"""
    print("=" * 60)
    print("知识库数据导入脚本")
    print("整合三种数据源：HuggingFace、维基百科、GitHub教材")
    print("=" * 60)
    
    all_items = []
    
    # 1. 从HuggingFace加载（基础概念层）
    print("\n[方案一] 加载HuggingFace数据集...")
    hf_items = load_from_huggingface(max_items=50)
    all_items.extend(hf_items)
    
    # 2. 从维基百科加载（知识扩充层）
    print("\n[方案二] 加载维基百科数据...")
    wiki_items = load_from_wikipedia(max_items=50)
    all_items.extend(wiki_items)
    
    # 3. 从GitHub加载（课程结构层）
    print("\n[方案三] 加载GitHub教材数据...")
    github_items = load_from_github(max_items=50)
    all_items.extend(github_items)
    
    # 统计各分类数量
    print("\n" + "=" * 60)
    print("数据统计:")
    category_counts = {}
    for item in all_items:
        cat = item.get("category", "other")
        category_counts[cat] = category_counts.get(cat, 0) + 1
    
    for cat, count in sorted(category_counts.items()):
        print(f"  {cat}: {count} 条")
    
    print(f"\n总计: {len(all_items)} 条数据")
    print("=" * 60)
    
    # 保存知识库
    if all_items:
        save_knowledge_base(all_items)
        print("\n知识库导入完成！")
    else:
        print("\n警告: 未导入任何数据")
        print("请检查:")
        print("1. pip install datasets (用于HuggingFace)")
        print("2. 下载维基百科数据到 scripts/wiki_output/")
        print("3. 确保git已安装 (用于GitHub克隆)")


if __name__ == "__main__":
    main()