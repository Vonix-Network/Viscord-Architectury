import os
import re

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content

    # Component.literal -> new TextComponent
    content = content.replace('Component.literal', 'new TextComponent')
    
    # CommandRegistrationEvent parameters
    content = content.replace('(dispatcher, registry, selection) ->', '(dispatcher, selection) ->')

    # TextColor.fromRgb("#...") -> TextColor.fromRgb(0x...)
    content = re.sub(r'TextColor\.fromRgb\("#([A-Fa-f0-9]+)"\)', r'TextColor.fromRgb(0x\1)', content)

    # sendSystemMessage(message, false) -> sendMessage(message, net.minecraft.Util.NIL_UUID)
    content = content.replace('sendSystemMessage(message, false)', 'sendMessage(message, net.minecraft.Util.NIL_UUID)')

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
