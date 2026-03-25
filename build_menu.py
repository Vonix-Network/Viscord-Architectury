#!/usr/bin/env python3
"""
Viscord Build Menu v3.0 - Gamified Terminal UI
A beautiful, interactive CLI interface for building Viscord mods
"""

import os
import sys
import re
import subprocess
import tempfile
import shutil
import time
import random
from pathlib import Path
from datetime import datetime
from typing import Optional, List, Dict, Any
from dataclasses import dataclass
from enum import Enum

from rich.console import Console
from rich.panel import Panel
from rich.table import Table
from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn, TaskProgressColumn
from rich.layout import Layout
from rich.text import Text
from rich import box
from rich.align import Align
from rich.prompt import Prompt, Confirm
from rich.columns import Columns
from rich.live import Live
from rich.tree import Tree
from rich.rule import Rule

# Force terminal reset
def clear_screen():
    """Clear the terminal screen in a cross-platform way"""
    if os.name == 'nt':
        os.system('cls')
    else:
        os.system('clear')
    console.clear()

console = Console()


class BuildType(Enum):
    CLEAN = "clean"
    QUICK = "quick"


@dataclass
class JavaVersion:
    path: str
    version: int
    full_path: str
    display_name: str
    full_version: str


@dataclass
class BuildResult:
    success: bool
    output: List[str]
    error: List[str]
    exit_code: int


# ASCII Art Logo
LOGO = """
[bold cyan]╔══════════════════════════════════════════════════════════════╗[/bold cyan]
[bold cyan]║[/bold cyan]     [bold yellow]██╗   ██╗██╗███████╗ ██████╗ ██████╗ ██████╗ ██████╗[/bold yellow]    [bold cyan]║[/bold cyan]
[bold cyan]║[/bold cyan]     [bold yellow]██║   ██║██║██╔════╝██╔════╝██╔═══██╗██╔══██╗██╔══██╗[/bold yellow]   [bold cyan]║[/bold cyan]
[bold cyan]║[/bold cyan]     [bold yellow]██║   ██║██║███████╗██║     ██║   ██║██████╔╝██║  ██║[/bold yellow]   [bold cyan]║[/bold cyan]
[bold cyan]║[/bold cyan]     [bold yellow]╚██╗ ██╔╝██║╚════██║██║     ██║   ██║██╔══██╗██║  ██║[/bold yellow]   [bold cyan]║[/bold cyan]
[bold cyan]║[/bold cyan]      [bold yellow]╚████╔╝ ██║███████║╚██████╗╚██████╔╝██║  ██║██████╔╝[/bold yellow]   [bold cyan]║[/bold cyan]
[bold cyan]║[/bold cyan]       [bold yellow]╚═══╝  ╚═╝╚══════╝ ╚═════╝ ╚═════╝ ╚═╝  ╚═╝╚═════╝[/bold yellow]    [bold cyan]║[/bold cyan]
[bold cyan]╠══════════════════════════════════════════════════════════════╣[/bold cyan]
[bold cyan]║[/bold cyan]         [italic bright_green]The Ultimate Minecraft-Discord Bridge[/italic bright_green]          [bold cyan]║[/bold cyan]
[bold cyan]╚══════════════════════════════════════════════════════════════╝[/bold cyan]
"""

# Gamified messages
BUILD_SUCCESS_MESSAGES = [
    "🔥 Build Legendary!",
    "⚡ Maximum Power Achieved!",
    "🎯 Perfect Execution!",
    "🚀 Mission Accomplished!",
    "💎 Master Craftsman!",
    "🏆 Victory! Build Complete!",
    "✨ Build Enchanted Successfully!",
    "🎮 Achievement Unlocked: Build Master!"
]

BUILD_FAIL_MESSAGES = [
    "💀 Critical Failure...",
    "🔥 The Build Burnt to Ash...",
    "⚠️  Catastrophic Error!",
    "💥 Build Crashed and Burned!",
    "🌋 Lava Flow Detected!",
    "👻 Phantom Build Errors!",
    "🕸️  Caught in a Web of Bugs!"
]

RARE_MESSAGES = [
    "🌟 Legendary Build Detected!",
    "💫 Mythical Success!",
    "🏅 Epic Achievement Unlocked!",
    "🎊 Unbelievable Performance!"
]


class ViscordBuildMenu:
    VERSIONS = ["1.18.2", "1.19.2", "1.20.1", "1.21.1"]
    
    def __init__(self):
        self.root_dir = Path.cwd()
        self.selected_java: Optional[JavaVersion] = None
        self.mod_version = self._get_mod_version()
        self.total_builds = 0
        self.successful_builds = 0
        self.session_start = datetime.now()
        
    def _get_mod_version(self) -> str:
        """Read version from gradle.properties"""
        for gradle_props in self.root_dir.rglob("gradle.properties"):
            try:
                content = gradle_props.read_text()
                match = re.search(r"^mod_version\s*=\s*(.+)$", content, re.MULTILINE)
                if match:
                    return match.group(1).strip()
            except Exception:
                pass
        return "2.4.12"
    
    def _display_stats(self):
        """Display gamified session stats"""
        duration = datetime.now() - self.session_start
        minutes = int(duration.total_seconds() / 60)
        seconds = int(duration.total_seconds() % 60)
        
        if self.total_builds > 0:
            success_rate = (self.successful_builds / self.total_builds) * 100
        else:
            success_rate = 0
        
        # Calculate "level" based on successful builds
        level = self.successful_builds // 4 + 1
        xp = (self.successful_builds % 4) * 25
        
        stats_text = f"""[bold cyan]🏅 Level {level}[/bold cyan]    [yellow]⭐ XP: {xp}/100[/yellow]    [green]✅ {success_rate:.0f}% Win Rate[/green]    [blue]⏱️  {minutes}m {seconds}s[/blue]"""
        console.print(Align.center(Panel(stats_text, border_style="bright_blue", box=box.ROUNDED, padding=(0, 2))))
    
    def _display_header(self, title: str, subtitle: str = ""):
        """Display a beautiful gamified header"""
        clear_screen()
        console.print(LOGO)
        
        if title:
            title_panel = Panel(
                Align.center(Text(title, style="bold bright_yellow")),
                box=box.DOUBLE_EDGE,
                border_style="bright_cyan",
                padding=(0, 4)
            )
            console.print(title_panel)
        
        if subtitle:
            console.print(Align.center(f"[italic bright_green]{subtitle}[/italic bright_green]"))
        
        self._display_stats()
        console.print()
    
    def _get_installed_java_versions(self) -> List[JavaVersion]:
        """Detect installed Java versions"""
        java_versions = []
        
        # Check Eclipse Adoptium
        adoptium_path = Path(os.environ.get("LOCALAPPDATA", "")) / "Programs" / "Eclipse Adoptium"
        if adoptium_path.exists():
            for java_dir in adoptium_path.iterdir():
                if java_dir.is_dir():
                    java_exe = java_dir / "bin" / "java.exe"
                    if java_exe.exists():
                        try:
                            result = subprocess.run([str(java_exe), "-version"], 
                                                  capture_output=True, text=True, timeout=5)
                            version_output = result.stderr or result.stdout
                            match = re.search(r'"([0-9.]+)"', version_output)
                            if match:
                                full_version = match.group(1)
                                major_version = int(full_version.split(".")[0])
                                java_versions.append(JavaVersion(
                                    path=str(java_exe),
                                    version=major_version,
                                    full_path=str(java_dir),
                                    display_name=f"Java {major_version} ({java_dir.name})",
                                    full_version=full_version
                                ))
                        except Exception:
                            pass
        
        # Check Program Files Java
        program_files_java = Path(os.environ.get("ProgramFiles", "")) / "Java"
        if program_files_java.exists():
            for java_dir in program_files_java.iterdir():
                if java_dir.is_dir():
                    java_exe = java_dir / "bin" / "java.exe"
                    if java_exe.exists():
                        try:
                            result = subprocess.run([str(java_exe), "-version"],
                                                  capture_output=True, text=True, timeout=5)
                            version_output = result.stderr or result.stdout
                            match = re.search(r'"([0-9.]+)"', version_output)
                            if match:
                                full_version = match.group(1)
                                major_version = int(full_version.split(".")[0])
                                # Check for duplicates
                                if not any(j.full_path == str(java_dir) for j in java_versions):
                                    java_versions.append(JavaVersion(
                                        path=str(java_exe),
                                        version=major_version,
                                        full_path=str(java_dir),
                                        display_name=f"Java {major_version} ({java_dir.name})",
                                        full_version=full_version
                                    ))
                        except Exception:
                            pass
        
        # Check JAVA_HOME
        java_home = os.environ.get("JAVA_HOME", "")
        if java_home and Path(java_home).exists():
            java_exe = Path(java_home) / "bin" / "java.exe"
            if java_exe.exists():
                try:
                    result = subprocess.run([str(java_exe), "-version"],
                                          capture_output=True, text=True, timeout=5)
                    version_output = result.stderr or result.stdout
                    match = re.search(r'"([0-9.]+)"', version_output)
                    if match:
                        full_version = match.group(1)
                        major_version = int(full_version.split(".")[0])
                        if not any(j.full_path == java_home for j in java_versions):
                            java_versions.append(JavaVersion(
                                path=str(java_exe),
                                version=major_version,
                                full_path=java_home,
                                display_name=f"Java {major_version} (JAVA_HOME)",
                                full_version=full_version
                            ))
                except Exception:
                    pass
        
        # Check PATH
        try:
            result = subprocess.run(["java", "-version"],
                                  capture_output=True, text=True, timeout=5)
            version_output = result.stderr or result.stdout
            match = re.search(r'"([0-9.]+)"', version_output)
            if match:
                full_version = match.group(1)
                major_version = int(full_version.split(".")[0])
                # Try to find the Java home from PATH
                java_cmd = shutil.which("java")
                if java_cmd:
                    java_home = str(Path(java_cmd).parent.parent)
                    if not any(j.full_path == java_home for j in java_versions):
                        java_versions.append(JavaVersion(
                            path=java_cmd,
                            version=major_version,
                            full_path=java_home,
                            display_name=f"Java {major_version} (PATH)",
                            full_version=full_version
                        ))
        except Exception:
            pass
        
        return sorted(java_versions, key=lambda x: x.version, reverse=True)
    
    def _get_required_java_version(self, mc_version: str) -> int:
        """All versions require Java 21 due to newer Architectury Loom"""
        return 21
    
    def _set_optimal_java(self, mc_version: str) -> Optional[JavaVersion]:
        """Auto-select best Java version for the Minecraft version"""
        required = self._get_required_java_version(mc_version)
        java_versions = self._get_installed_java_versions()
        
        for java in sorted(java_versions, key=lambda x: x.version):
            if java.version >= required:
                console.print(f"[bold cyan]⚡ Auto-equipping {java.display_name} for Minecraft {mc_version}[/bold cyan]")
                return java
        
        if self.selected_java:
            console.print(f"[bold yellow]🎒 Equipping user-selected {self.selected_java.display_name} for Minecraft {mc_version}[/bold yellow]")
            return self.selected_java
        
        console.print(f"[bold yellow]⚠️  Using default system Java for Minecraft {mc_version}[/bold yellow]")
        return None
    
    def _display_header(self, title: str):
        """Display a beautiful header"""
        console.clear()
        header = Panel(
            Align.center(Text(title, style="bold white")),
            box=box.ROUNDED,
            style="cyan",
            border_style="cyan"
        )
        console.print(header)
        console.print()
    
    def _display_menu(self):
        """Display the gamified main menu"""
        self._display_header("🎮 MAIN MENU", "Choose Your Adventure")
        
        # Create menu with gamified styling
        menu_items = [
            ("[bold bright_green]⚔️  QUEST 1[/bold bright_green]", "Build All → Releases", "Conquer all dimensions at once"),
            ("[bold bright_blue]🗡️  QUEST 2[/bold bright_blue]", "Build All → Versioned", "Organize your loot by world"),
            ("[bold bright_yellow]🛡️  QUEST 3[/bold bright_yellow]", "Build Specific Version", "Target a single realm"),
            ("[bold bright_magenta]🏰 QUEST 4[/bold bright_magenta]", "Build to Custom Folder", "Forge your own path"),
            ("[bold bright_cyan]⚙️  SETTINGS[/bold bright_cyan]", f"Java Engine", f"Current: {self.selected_java.display_name if self.selected_java else 'Default'}"),
            ("[bold red]🚪 EXIT[/bold red]", "Quit Game", "Save and exit to desktop"),
        ]
        
        for i, (label, action, desc) in enumerate(menu_items, 1):
            color = "green" if i <= 4 else ("cyan" if i == 5 else "red")
            
            row = Table(show_header=False, box=None, padding=0)
            row.add_column(width=20)
            row.add_column(width=25)
            row.add_column()
            
            if i == 6:
                row.add_row(label, f"[bold red]{action}[/bold red]", f"[dim]{desc}[/dim]")
            else:
                row.add_row(label, f"[bold white]{action}[/bold white]", f"[dim]{desc}[/dim]")
            
            panel = Panel(row, border_style=color, box=box.ROUNDED, padding=(0, 1))
            console.print(Align.center(panel))
            console.print()
        
        # Version badge
        version_badge = Panel(
            f"[bold bright_cyan]📦 VISCORD v{self.mod_version}[/bold bright_cyan]    [dim]⚡ Powered by Architectury[/dim]",
            box=box.ROUNDED,
            border_style="dim cyan"
        )
        console.print(Align.center(version_badge))
        console.print()
    
    def _select_build_type(self) -> BuildType:
        """Gamified build type selection"""
        clear_screen()
        self._display_header("⚡ SELECT BUILD MODE", "Choose your forging technique")
        
        table = Table(show_header=False, box=box.HEAVY_EDGE, border_style="bright_yellow")
        table.add_column("Mode", style="bold bright_yellow", justify="center")
        table.add_column("Name", style="bold white")
        table.add_column("Power", style="bright_green")
        table.add_column("Description", style="dim white")
        
        table.add_row(
            "[⚔️]",
            "CLEAN FORGE",
            "██████████ 100%",
            "Complete reconstruction - Maximum purity, zero artifacts"
        )
        table.add_row(
            "[🏃]",
            "QUICK FORGE",
            "██████░░░░ 60%",
            "Incremental build - Fast but may retain shadows"
        )
        
        console.print(Align.center(table))
        console.print()
        
        choice = Prompt.ask(
            "[bold cyan]Choose your forging technique[/bold cyan]",
            choices=["1", "2"],
            default="1"
        )
        
        return BuildType.CLEAN if choice == "1" else BuildType.QUICK
    
    def _select_java_version(self):
        """Gamified Java version selection"""
        clear_screen()
        self._display_header("⚙️  JAVA ENGINE SELECTION", "Select your power source")
        
        with console.status("[bold yellow]Scanning for Java engines...[/bold yellow]", spinner="bouncing") as status:
            java_versions = self._get_installed_java_versions()
        
        if not java_versions:
            console.print("[bold red on black]💀 NO JAVA ENGINES DETECTED![/bold red on black]")
            console.print("[yellow]Please install Java 21 or higher to continue your quest.[/yellow]")
            Prompt.ask("[dim]Press Enter to return...[/dim]")
            return
        
        console.print(f"[green]✓ Found {len(java_versions)} Java engines[/green]\n")
        
        # Display Java versions as cards
        table = Table(show_header=True, header_style="bold bright_cyan", box=box.HEAVY_EDGE)
        table.add_column("Slot", style="bold yellow", justify="center")
        table.add_column("Engine", style="white")
        table.add_column("Version", style="bright_green", justify="center")
        table.add_column("Power Level", style="bright_magenta")
        
        for i, java in enumerate(java_versions, 1):
            power_bars = "█" * min(java.version - 8, 10) + "░" * (10 - min(java.version - 8, 10))
            table.add_row(f"[{i}]", java.display_name, str(java.version), power_bars)
        
        table.add_row("[0]", "[dim]System Default[/dim]", "-", "[dim]Auto-detect[/dim]")
        
        console.print(Align.center(table))
        console.print()
        
        choices = [str(i) for i in range(len(java_versions) + 1)]
        choice = Prompt.ask("[bold cyan]Select engine slot[/bold cyan]", choices=choices, default="0")
        
        if choice == "0":
            self.selected_java = None
            console.print("[yellow]⚙️  Reverted to system default[/yellow]")
        else:
            self.selected_java = java_versions[int(choice) - 1]
            console.print(f"[bold green]✅ Equipped: {self.selected_java.display_name}[/bold green]")
        
        Prompt.ask("[dim]Press Enter to continue...[/dim]")
    
    def _build_with_progress(self, project_name: str, mc_version: str, build_type: BuildType) -> BuildResult:
        """Execute Gradle build with visual progress tracking"""
        status_text = "Clean Build" if build_type == BuildType.CLEAN else "Quick Build"
        
        with Progress(
            SpinnerColumn(),
            TextColumn("[progress.description]{task.description}"),
            BarColumn(complete_style="green", finished_style="green"),
            TaskProgressColumn(),
            console=console,
            transient=False
        ) as progress:
            
            task = progress.add_task(f"[cyan]{status_text} - {project_name}...", total=100)
            
            output_file = tempfile.mktemp(suffix=".txt", prefix="gradle_output_")
            error_file = tempfile.mktemp(suffix=".txt", prefix="gradle_error_")
            
            try:
                args = ["clean", "build"] if build_type == BuildType.CLEAN else ["build"]
                
                # Use cmd.exe /c to run gradlew.bat properly on Windows
                cmd = ["cmd.exe", "/c", "gradlew.bat"] + args
                
                with open(output_file, "w") as out_f, open(error_file, "w") as err_f:
                    process = subprocess.Popen(
                        cmd,
                        stdout=out_f,
                        stderr=err_f,
                        shell=False
                    )
                
                start_time = datetime.now()
                timeout = 300  # 5 minutes
                current_progress = 0
                
                while process.poll() is None:
                    elapsed = (datetime.now() - start_time).total_seconds()
                    
                    if elapsed > timeout:
                        process.terminate()
                        break
                    
                    # Time-based progress
                    time_progress = min(int(elapsed * 2), 85)
                    current_progress = max(current_progress, time_progress)
                    
                    # Try to read output for better status
                    try:
                        with open(output_file, "r", errors="ignore") as f:
                            lines = f.readlines()
                            if lines:
                                last_lines = "".join(lines[-10:])
                                if ":" in last_lines:
                                    task_match = re.search(r":(\w+):", last_lines)
                                    if task_match:
                                        task_name = task_match.group(1)
                                        progress.update(task, description=f"[cyan]{status_text} - {task_name}...")
                                
                                # Check for specific phases
                                if re.search(r"clean|Clean", last_lines):
                                    current_progress = max(current_progress, 15)
                                elif re.search(r"compiling|Compiling", last_lines, re.IGNORECASE):
                                    current_progress = max(current_progress, 50)
                                elif re.search(r"jar|Jar", last_lines):
                                    current_progress = max(current_progress, 85)
                                elif "BUILD SUCCESSFUL" in last_lines:
                                    current_progress = 100
                    except Exception:
                        pass
                    
                    progress.update(task, completed=current_progress)
                    import time
                    time.sleep(0.5)
                
                exit_code = process.returncode
                
                # Read output
                try:
                    with open(output_file, "r", errors="ignore") as f:
                        output_lines = f.read().splitlines()
                except Exception:
                    output_lines = []
                
                try:
                    with open(error_file, "r", errors="ignore") as f:
                        error_lines = f.read().splitlines()
                except Exception:
                    error_lines = []
                
                progress.update(task, completed=100, description=f"[green]{status_text} Complete![/green]")
                
                success = exit_code == 0 or any("BUILD SUCCESSFUL" in line for line in output_lines)
                
                return BuildResult(
                    success=success,
                    output=output_lines,
                    error=error_lines,
                    exit_code=exit_code
                )
                
            except Exception as e:
                return BuildResult(
                    success=False,
                    output=[],
                    error=[str(e)],
                    exit_code=1
                )
            finally:
                # Clean up temp files
                try:
                    os.unlink(output_file)
                    os.unlink(error_file)
                except Exception:
                    pass
    
    def _copy_jars(self, version: str, dest_dir: Path, rename: bool = True) -> bool:
        """Copy built JARs with gamified loot collection"""
        platforms = []
        if (Path("fabric")).exists():
            platforms.append("fabric")
        if (Path("forge")).exists():
            platforms.append("forge")
        if (Path("neoforge")).exists():
            platforms.append("neoforge")
        
        if not platforms:
            console.print("[dim]💨 No artifacts found in this realm...[/dim]")
            return False
        
        console.print(f"[cyan]🌍 Discovered platforms: {', '.join(platforms)}[/cyan]")
        
        copied = False
        for platform in platforms:
            libs_path = Path(platform) / "build" / "libs"
            if not libs_path.exists():
                console.print(f"[dim]🌫️  {platform} realm is empty[/dim]")
                continue
            
            jar_patterns = [
                f"viscord-{platform}-*.jar",
                f"viscord-*-{platform}.jar",
                f"viscord-{platform}.jar"
            ]
            
            found = False
            for pattern in jar_patterns:
                jars = list(libs_path.glob(pattern))
                for jar in jars:
                    # Skip dev/shadow/sources jars
                    if any(x in jar.name for x in ["-dev-shadow", "-sources", "-transform"]):
                        continue
                    
                    if rename:
                        new_name = f"viscord-{version}-{platform}-{self.mod_version}.jar"
                        dest_path = dest_dir / new_name
                    else:
                        dest_path = dest_dir / jar.name
                    
                    shutil.copy2(jar, dest_path)
                    
                    # Gamified loot message
                    size_kb = jar.stat().st_size / 1024
                    console.print(f"[bold bright_green]💎 LOOT ACQUIRED: {dest_path.name}[/bold bright_green] [dim]({size_kb:.1f} KB)[/dim]")
                    found = True
                    copied = True
                
                if found:
                    break
            
            if not found:
                console.print(f"[yellow]⚠️  No artifacts in {platform} realm[/yellow]")
        
        return copied
    
    def _get_version_dir(self, version: str) -> Optional[Path]:
        """Find the version directory"""
        pattern = f"viscord-{version}-*"
        for item in self.root_dir.iterdir():
            if item.is_dir() and item.name.startswith(f"viscord-{version}-"):
                return item
        return None
    
    def _build_version(self, version: str, build_type: BuildType) -> BuildResult:
        """Build a single version with gamified feedback"""
        ver_dir = self._get_version_dir(version)
        if not ver_dir:
            console.print(f"[bold red]💀 Realm {version} not found![/bold red]")
            return BuildResult(False, [], [f"Directory not found for {version}"], 1)
        
        os.chdir(ver_dir)
        
        # Set optimal Java
        optimal_java = self._set_optimal_java(version)
        original_java_home = os.environ.get("JAVA_HOME", "")
        original_path = os.environ.get("PATH", "")
        
        try:
            if optimal_java:
                os.environ["JAVA_HOME"] = optimal_java.full_path
                os.environ["PATH"] = str(Path(optimal_java.full_path) / "bin") + ";" + original_path
            
            result = self._build_with_progress(f"Minecraft {version}", version, build_type)
        finally:
            os.environ["JAVA_HOME"] = original_java_home
            os.environ["PATH"] = original_path
            os.chdir(self.root_dir)
        
        self.total_builds += 1
        if result.success:
            self.successful_builds += 1
        
        return result
    
    def _display_build_error(self, result: BuildResult):
        """Display build error with gamified failure"""
        console.print()
        fail_msg = random.choice(BUILD_FAIL_MESSAGES)
        console.print(Panel(
            Align.center(Text(fail_msg, style="bold red")),
            border_style="red",
            box=box.HEAVY
        ))
        console.print(f"[red]Exit Code: {result.exit_code}[/red]")
        
        if result.output:
            console.print("\n[bold dark_red]📜 Scroll of Errors (last 30 lines):[/bold dark_red]")
            for line in result.output[-30:]:
                if line.strip():
                    console.print(f"[red]  ► {line}[/red]")
        
        if result.error:
            console.print("\n[bold dark_red]💀 Critical Failures:[/bold dark_red]")
            for line in result.error[-10:]:
                if line.strip():
                    console.print(f"[red]  ✗ {line}[/red]")
    
    def build_all_releases(self):
        """Build all versions to Releases folder with gamification"""
        build_type = self._select_build_type()
        
        clear_screen()
        self._display_header("⚔️  EPIC QUEST: BUILD ALL TO RELEASES", "Forge artifacts for all dimensions")
        
        releases_dir = self.root_dir / "Releases"
        releases_dir.mkdir(exist_ok=True)
        console.print(f"[green]📁 Treasury initialized: {releases_dir}[/green]\n")
        
        total = len(self.VERSIONS)
        
        for i, version in enumerate(self.VERSIONS, 1):
            console.print(Rule(f"[bold yellow]🌟 Dimension {i}/{total}: Minecraft {version}[/bold yellow]", style="bright_yellow"))
            
            result = self._build_version(version, build_type)
            
            if result.success:
                self._display_success_message()
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, releases_dir, rename=True)
                os.chdir(self.root_dir)
            else:
                self._display_build_error(result)
            
            console.print()
        
        self._display_quest_complete(total)
    
    def build_all_versioned(self):
        """Build all versions to versioned folders with gamification"""
        build_type = self._select_build_type()
        
        clear_screen()
        self._display_header("🗡️  QUEST: ORGANIZE BY DIMENSION", "Sort artifacts into realm vaults")
        
        total = len(self.VERSIONS)
        
        for version in self.VERSIONS:
            console.print(Rule(f"[bold cyan]🌍 Processing Realm: {version}[/bold cyan]", style="bright_cyan"))
            
            result = self._build_version(version, build_type)
            
            if result.success:
                self._display_success_message()
                version_dir = self.root_dir / version
                version_dir.mkdir(exist_ok=True)
                
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, version_dir, rename=False)
                os.chdir(self.root_dir)
            else:
                self._display_build_error(result)
            
            console.print()
        
        self._display_quest_complete(total)
    
    def build_specific_version(self):
        """Build a specific Minecraft version with gamification"""
        clear_screen()
        self._display_header("🛡️  SELECT YOUR TARGET", "Choose a dimension to conquer")
        
        table = Table(show_header=True, header_style="bold bright_cyan", box=box.HEAVY_EDGE)
        table.add_column("Quest", style="bold yellow", justify="center")
        table.add_column("Dimension", style="white")
        table.add_column("Difficulty", style="bright_magenta")
        
        difficulties = ["⭐⭐", "⭐⭐⭐", "⭐⭐⭐⭐", "⭐⭐⭐⭐⭐"]
        for i, v in enumerate(self.VERSIONS, 1):
            table.add_row(f"[{i}]", f"Minecraft {v}", difficulties[i-1])
        
        table.add_row("[5]", "[dim]Return to Base Camp[/dim]", "-")
        
        console.print(Align.center(table))
        console.print()
        
        choice = Prompt.ask("[bold cyan]Select your target[/bold cyan]", choices=["1", "2", "3", "4", "5"])
        
        if choice == "5":
            return
        
        version = self.VERSIONS[int(choice) - 1]
        build_type = self._select_build_type()
        
        clear_screen()
        self._display_header(f"⚔️  CONQUERING: MINECRAFT {version}", "Single dimension assault")
        
        result = self._build_version(version, build_type)
        
        if result.success:
            self._display_success_message()
            console.print()
            console.print("[bold cyan]🎒 Where shall we store the artifact?[/bold cyan]")
            
            dest_choice = Prompt.ask(
                "[cyan]Choose storage[/cyan]",
                choices=["1", "2", "3"],
                default="3"
            )
            
            if dest_choice == "1":
                releases_dir = self.root_dir / "Releases"
                releases_dir.mkdir(exist_ok=True)
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, releases_dir, rename=True)
                os.chdir(self.root_dir)
            elif dest_choice == "2":
                version_dir = self.root_dir / version
                version_dir.mkdir(exist_ok=True)
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, version_dir, rename=False)
                os.chdir(self.root_dir)
            else:
                console.print("[yellow]📦 Artifacts left at forge[/yellow]")
        else:
            self._display_build_error(result)
        
        Prompt.ask("[dim]Press Enter to return...[/dim]")
    
    def build_custom_folder(self):
        """Build to custom folder with gamification"""
        clear_screen()
        self._display_header("🏰 CUSTOM ADVENTURE", "Forge your own destiny")
        
        custom_folder = Prompt.ask("[bold cyan]Name your custom vault[/bold cyan]")
        
        if not custom_folder.strip():
            console.print("[bold red]💀 Vault name cannot be empty![/bold red]")
            Prompt.ask("[dim]Press Enter...[/dim]")
            return self.build_custom_folder()
        
        build_type = self._select_build_type()
        
        clear_screen()
        self._display_header(f"🏰 QUEST: FORGE TO {custom_folder.upper()}", "Custom dimension expedition")
        
        custom_dir = self.root_dir / custom_folder
        custom_dir.mkdir(exist_ok=True)
        
        total = len(self.VERSIONS)
        
        for version in self.VERSIONS:
            console.print(Rule(f"[bold magenta]🌌 Processing {version}...[/bold magenta]", style="bright_magenta"))
            
            result = self._build_version(version, build_type)
            
            if result.success:
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, custom_dir, rename=True)
                os.chdir(self.root_dir)
            else:
                self._display_build_error(result)
            
            console.print()
        
        self._display_quest_complete(total)
    
    def _display_success_message(self):
        """Display random success message"""
        msg = random.choice(BUILD_SUCCESS_MESSAGES)
        if random.random() < 0.05:  # 5% chance for rare message
            msg = random.choice(RARE_MESSAGES)
        
        console.print(Panel(
            Align.center(Text(msg, style="bold bright_green")),
            border_style="bright_green",
            box=box.HEAVY_EDGE
        ))

    def _display_summary(self, success_count: int, total_count: int, destination: str):
        """Display build summary"""
        console.print()
        console.print(Panel(
            Align.center(Text("BUILD PROCESS COMPLETED", style="bold green")),
            box=box.ROUNDED,
            border_style="green"
        ))
        
        summary = Table(show_header=False, box=box.ROUNDED, border_style="cyan")
        summary.add_column("Metric", style="cyan")
        summary.add_column("Value", style="white")
        summary.add_row("Successful Builds", f"[green]{success_count}/{total_count}[/green]")
        summary.add_row("Destination", f"[white]{destination}[/white]")
        
        console.print(Align.center(summary))
        console.print()
        Prompt.ask("[cyan]Press Enter to continue...[/cyan]")
    
    def run(self):
        """Main game loop"""
        while True:
            self._display_menu()
            
            choice = Prompt.ask(
                "[bold bright_cyan]Select your quest[/bold bright_cyan]",
                choices=["1", "2", "3", "4", "5", "6"]
            )
            
            if choice == "1":
                self.build_all_releases()
            elif choice == "2":
                self.build_all_versioned()
            elif choice == "3":
                self.build_specific_version()
            elif choice == "4":
                self.build_custom_folder()
            elif choice == "5":
                self._select_java_version()
            elif choice == "6":
                self._display_exit_screen()
                break
    
    def _display_exit_screen(self):
        """Display epic exit screen"""
        clear_screen()
        
        duration = datetime.now() - self.session_start
        minutes = int(duration.total_seconds() / 60)
        
        exit_art = f"""
[bold bright_cyan]╔═══════════════════════════════════════════════════════════════════╗[/bold bright_cyan]
[bold bright_cyan]║[/bold bright_cyan]                                                                   [bold bright_cyan]║[/bold bright_cyan]
[bold bright_cyan]║[/bold bright_cyan]    [bold bright_yellow]╔═══════════════════════════════════════════════════════════╗[/bold bright_yellow]     [bold bright_cyan]║[/bold bright_cyan]
[bold bright_cyan]║[/bold bright_cyan]    [bold bright_yellow]║[/bold bright_yellow]                                                           [bold bright_yellow]║[/bold bright_yellow]     [bold bright_cyan]║[/bold bright_cyan]
[bold bright_cyan]║[/bold bright_cyan]    [bold bright_yellow]║[/bold bright_yellow]   [bold bright_green]THANKS FOR FORGING WITH VISCORD BUILD MENU![/bold bright_green]       [bold bright_yellow]║[/bold bright_yellow]     [bold bright_cyan]║[/bold bright_cyan]
[bold bright_cyan]║[/bold bright_cyan]    [bold bright_yellow]║[/bold bright_yellow]                                                           [bold bright_yellow]║[/bold bright_yellow]     [bold bright_cyan]║[/bold bright_cyan]
[bold bright_cyan]║[/bold bright_cyan]    [bold bright_yellow]║[/bold bright_yellow]      [dim]Session Duration: {minutes} minutes[/dim]                           [bold bright_yellow]║[/bold bright_yellow]     [bold bright_cyan]║[/bold bright_cyan]
[bold bright_cyan]║[/bold bright_cyan]    [bold bright_yellow]║[/bold bright_yellow]      [dim]Builds Completed: {self.total_builds}[/dim]                               [bold bright_yellow]║[/bold bright_yellow]     [bold bright_cyan]║[/bold bright_cyan]
[bold bright_cyan]║[/bold bright_cyan]    [bold bright_yellow]║[/bold bright_yellow]                                                           [bold bright_yellow]║[/bold bright_yellow]     [bold bright_cyan]║[/bold bright_cyan]
[bold bright_cyan]║[/bold bright_cyan]    [bold bright_yellow]║[/bold bright_yellow]        [italic bright_cyan]May your builds always succeed![/italic bright_cyan]               [bold bright_yellow]║[/bold bright_yellow]     [bold bright_cyan]║[/bold bright_cyan]
[bold bright_cyan]║[/bold bright_cyan]    [bold bright_yellow]║[/bold bright_yellow]                                                           [bold bright_yellow]║[/bold bright_yellow]     [bold bright_cyan]║[/bold bright_cyan]
[bold bright_cyan]║[/bold bright_cyan]    [bold bright_yellow]╚═══════════════════════════════════════════════════════════╝[/bold bright_yellow]     [bold bright_cyan]║[/bold bright_cyan]
[bold bright_cyan]║[/bold bright_cyan]                                                                   [bold bright_cyan]║[/bold bright_cyan]
[bold bright_cyan]╚═══════════════════════════════════════════════════════════════════╝[/bold bright_cyan]
        """
        console.print(exit_art)
        time.sleep(1.5)
    
    def _display_quest_complete(self, total_count: int):
        """Display quest completion screen"""
        clear_screen()
        
        success_rate = (self.successful_builds / total_count) * 100 if total_count > 0 else 0
        
        # Victory banner
        banner = """
[bold bright_green]    ╔═══════════════════════════════════════════════════════════════╗[/bold bright_green]
[bold bright_green]    ║                                                               ║[/bold bright_green]
[bold bright_green]    ║     ⚔️  QUEST COMPLETED! ALL DIMENSIONS CONQUERED!  ⚔️         ║[/bold bright_green]
[bold bright_green]    ║                                                               ║[/bold bright_green]
[bold bright_green]    ╚═══════════════════════════════════════════════════════════════╝[/bold bright_green]
        """
        console.print(banner)
        
        # Stats table
        stats = Table(show_header=False, box=box.DOUBLE_EDGE, border_style="bright_cyan")
        stats.add_column("Stat", style="bold cyan")
        stats.add_column("Value", style="white")
        
        stats.add_row("🏆 Successful Forges", f"[bold green]{self.successful_builds}/{total_count}[/bold green]")
        stats.add_row("📦 Artifacts Created", f"[bold yellow]{self.successful_builds * 2}[/bold yellow]")
        stats.add_row("⭐ Success Rate", f"[bold bright_green]{success_rate:.0f}%[/bold bright_green]")
        stats.add_row("⚡ Total XP Gained", f"[bold bright_cyan]+{self.successful_builds * 25} XP[/bold bright_cyan]")
        
        console.print(Align.center(stats))
        console.print()
        
        if success_rate == 100:
            console.print(Align.center("[bold bright_yellow]🏅 PERFECT RUN! LEGENDARY STATUS ACHIEVED! 🏅[/bold bright_yellow]"))
        elif success_rate >= 75:
            console.print(Align.center("[bold green]🎖️  EXCELLENT WORK, CHAMPION! 🎖️[/bold green]"))
        elif success_rate >= 50:
            console.print(Align.center("[bold yellow]🎗️  GOOD EFFORT, WARRIOR! 🎗️[/bold yellow]"))
        else:
            console.print(Align.center("[bold red]⚠️  SOME DIMENSIONS RESISTED... TRY AGAIN! ⚠️[/bold red]"))
        
        console.print()
        Prompt.ask("[dim]Press Enter to return to base camp...[/dim]")


def main():
    """Entry point"""
    try:
        app = ViscordBuildMenu()
        app.run()
    except KeyboardInterrupt:
        console.print("\n[yellow]Interrupted by user. Goodbye![/yellow]")
        sys.exit(0)
    except Exception as e:
        console.print(f"\n[red]Error: {e}[/red]")
        sys.exit(1)


if __name__ == "__main__":
    main()
