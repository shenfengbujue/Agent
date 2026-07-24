import os
import json
import re
from datasets import load_dataset

os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"

def clean_text(text):
    text = re.sub(r'\s+', ' ', text).strip()
    text = re.sub(r'[^\u4e00-\u9fff0-9a-zA-Z��������������""''������������������\s]', '', text)
    return text

def extract_sections(text, max_sections=10):
    sections = []
    lines = text.split('\n')
    current_section = []
    
    for line in lines:
        line = line.strip()
        if not line:
            if current_section:
                section_text = ' '.join(current_section)
                if len(section_text) > 50:
                    sections.append(section_text[:500])
                current_section = []
            continue
        
        if line.startswith('## ') or line.startswith('### '):
            if current_section:
                section_text = ' '.join(current_section)
                if len(section_text) > 50:
                    sections.append(section_text[:500])
            current_section = [line]
        else:
            current_section.append(line)
    
    if current_section:
        section_text = ' '.join(current_section)
        if len(section_text) > 50:
            sections.append(section_text[:500])
    
    return sections[:max_sections]

def generate_learning_resources(dataset_name, output_file, num_samples=100):
    print(f"Loading dataset: {dataset_name}...")
    ds = load_dataset(dataset_name, split="train", streaming=True)
    
    resources = []
    categories = {
        'python': ['python', '���', '����', '����', '����'],
        'math': ['��ѧ', '����', '����', '΢����', '����'],
        'english': ['Ӣ��', 'Ӣ��', 'grammar', 'vocabulary'],
        'ai': ['�˹�����', '����ѧϰ', '���ѧϰ', '������'],
        'business': ['��ҵ', '����', 'Ӫ��', '����', '����'],
        'design': ['���', 'UI', 'UX', '�Ӿ�', '����']
    }
    
    for i, item in enumerate(ds):
        if i >= num_samples:
            break
        
        try:
            text = item.get('text', '')
            if not text or len(text) < 200:
                continue
            
            cleaned_text = clean_text(text)
            if len(cleaned_text) < 100:
                continue
            
            sections = extract_sections(cleaned_text)
            if not sections:
                continue
            
            matched_category = 'other'
            for cat, keywords in categories.items():
                if any(keyword in cleaned_text.lower() for keyword in keywords):
                    matched_category = cat
                    break
            
            resource = {
                'id': i + 1,
                'title': sections[0][:60] + '...' if len(sections[0]) > 60 else sections[0],
                'content': cleaned_text[:2000],
                'sections': sections,
                'category': matched_category,
                'source': dataset_name,
                'difficulty': '����' if i % 3 == 0 else ('�м�' if i % 3 == 1 else '�߼�'),
                'type': '����',
                'duration': f'{len(cleaned_text) // 500 + 1}����'
            }
            resources.append(resource)
            
            if (i + 1) % 10 == 0:
                print(f"Processed {i + 1}/{num_samples} samples")
                
        except Exception as e:
            print(f"Error processing sample {i}: {e}")
            continue
    
    print(f"Saving {len(resources)} resources to {output_file}...")
    os.makedirs(os.path.dirname(output_file), exist_ok=True)
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(resources, f, ensure_ascii=False, indent=2)
    
    print("Done!")
    return resources

if __name__ == "__main__":
    output_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'data')
    output_file = os.path.join(output_dir, 'knowledge_base.json')
    generate_learning_resources('opencsg/chinese-fineweb-edu-v2', output_file, num_samples=100)
