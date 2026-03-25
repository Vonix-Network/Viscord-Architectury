import os
import re

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content

    # Component.literal(...) -> new TextComponent(...)
    # Note: Component.literal("foo") -> new TextComponent("foo")
    content = re.sub(r'Component\.literal\((.*?)\)', r'new TextComponent(\1)', content)
    
    # Component.empty() -> new TextComponent("")
    content = content.replace('Component.empty()', 'new TextComponent("")')

    # TextColor.parseColor -> TextColor.fromRgb
    # Wait, in 1.18.2 TextColor.parseColor was TextColor.parseColor in text component?
    # Actually, the prompt says "uses TextColor.parseColor instead of TextColor.fromRgb"
    content = content.replace('TextColor.parseColor', 'TextColor.fromRgb')
    
    # packet.message() -> packet.getMessage()
    content = content.replace('packet.message()', 'packet.getMessage()')
    
    # event.getRawText() -> event.getMessage()
    content = content.replace('event.getRawText()', 'event.getMessage()')

    if filepath.endswith('PlayerAdvancementsMixin.java'):
        # getString() -> getContents() on Component in PlayerAdvancementsMixin
        content = content.replace('.getString()', '.getContents()')

    # Add import if needed
    if 'new TextComponent' in content and 'import net.minecraft.network.chat.TextComponent;' not in content:
        # Find the last import
        import_match = list(re.finditer(r'^import .*?;$', content, re.MULTILINE))
        if import_match:
            last_import = import_match[-1]
            insert_pos = last_import.end()
            content = content[:insert_pos] + '\nimport net.minecraft.network.chat.TextComponent;' + content[insert_pos:]
        else:
            # Add after package declaration
            pkg_match = re.search(r'^package .*?;$', content, re.MULTILINE)
            if pkg_match:
                insert_pos = pkg_match.end()
                content = content[:insert_pos] + '\n\nimport net.minecraft.network.chat.TextComponent;' + content[insert_pos:]

    if content != original_content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated {filepath}")

def main():
    target_dir = r"C:\Users\Admin\Development\Minecraft Mods\Work\Viscord-Architectury\viscord-1.18.2-fabric-forge-template"
    for root, dirs, files in os.walk(target_dir):
        for file in files:
            if file.endswith('.java'):
                process_file(os.path.join(root, file))

if __name__ == '__main__':
    main()
