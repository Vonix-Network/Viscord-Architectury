#!/usr/bin/env python3
"""
Viscord Build Menu - Modern Terminal UI
A beautiful, interactive CLI interface for building VonixSiteConnect mods
"""

import os
import sys
import re
import subprocess
import tempfile
import shutil
import time
from pathlib import Path
from datetime import datetime
from typing import Optional, List, Dict, Any
from dataclasses import dataclass
from enum import Enum

from rich.console import Console
from rich.panel import Panel
from rich.table import Table
from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn, TaskProgressColumn
from rich.text import Text
from rich import box
from rich.align import Align
from rich.prompt import Prompt, Confirm

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
    log_path: Optional[str] = None


class ViscordBuildMenu:
    VERSIONS = ["1.18.2", "1.19.2", "1.20.1", "1.21.1"]
    VERSION_PLATFORMS = {
        "1.18.2": ["fabric", "forge"],
        "1.19.2": ["fabric", "forge"],
        "1.20.1": ["fabric", "forge"],
        "1.21.1": ["fabric", "neoforge"],
    }

    def __init__(self):
        self.root_dir = Path.cwd()
        self.selected_java: Optional[JavaVersion] = None
        self.mod_version = self._get_mod_version()

    def _get_mod_version(self) -> str:
        for gradle_props in self.root_dir.rglob("gradle.properties"):
            try:
                content = gradle_props.read_text()
                match = re.search(r"^mod_version\s*=\s*(.+)$", content, re.MULTILINE)
                if match:
                    return match.group(1).strip()
            except Exception:
                pass
        return "0.1.0"

    def _get_installed_java_versions(self) -> List[JavaVersion]:
        java_versions = []

        # Check Eclipse Adoptium
        adoptium_path = Path(os.environ.get("LOCALAPPDATA", "")) / "Programs" / "Eclipse Adoptium"
        if adoptium_path.exists():
            for java_dir in adoptium_path.iterdir():
                if java_dir.is_dir():
                    java_exe = java_dir / "bin" / "java.exe"
                    if java_exe.exists():
                        self._try_add_java(java_versions, java_exe, java_dir, java_dir.name)

        # Check Program Files Java
        program_files_java = Path(os.environ.get("ProgramFiles", "")) / "Java"
        if program_files_java.exists():
            for java_dir in program_files_java.iterdir():
                if java_dir.is_dir():
                    java_exe = java_dir / "bin" / "java.exe"
                    if java_exe.exists() and not any(j.full_path == str(java_dir) for j in java_versions):
                        self._try_add_java(java_versions, java_exe, java_dir, java_dir.name)

        # Check JAVA_HOME
        java_home = os.environ.get("JAVA_HOME", "")
        if java_home and Path(java_home).exists():
            java_exe = Path(java_home) / "bin" / "java.exe"
            if java_exe.exists() and not any(j.full_path == java_home for j in java_versions):
                self._try_add_java(java_versions, java_exe, Path(java_home), "JAVA_HOME")

        # Check PATH
        try:
            result = subprocess.run(["java", "-version"], capture_output=True, text=True, timeout=5)
            version_output = result.stderr or result.stdout
            match = re.search(r'"([0-9.]+)"', version_output)
            if match:
                full_version = match.group(1)
                major_version = int(full_version.split(".")[0])
                java_cmd = shutil.which("java")
                if java_cmd:
                    java_home_path = str(Path(java_cmd).parent.parent)
                    if not any(j.full_path == java_home_path for j in java_versions):
                        java_versions.append(JavaVersion(
                            path=java_cmd, version=major_version,
                            full_path=java_home_path,
                            display_name=f"Java {major_version} (PATH)",
                            full_version=full_version
                        ))
        except Exception:
            pass

        return sorted(java_versions, key=lambda x: x.version, reverse=True)

    def _try_add_java(self, java_versions: List[JavaVersion], java_exe: Path, java_dir: Path, label: str):
        try:
            result = subprocess.run([str(java_exe), "-version"], capture_output=True, text=True, timeout=5)
            version_output = result.stderr or result.stdout
            match = re.search(r'"([0-9.]+)"', version_output)
            if match:
                full_version = match.group(1)
                major_version = int(full_version.split(".")[0])
                java_versions.append(JavaVersion(
                    path=str(java_exe), version=major_version,
                    full_path=str(java_dir),
                    display_name=f"Java {major_version} ({label})",
                    full_version=full_version
                ))
        except Exception:
            pass

    def _get_required_java_version(self, mc_version: str) -> int:
        return 21

    def _set_optimal_java(self, mc_version: str) -> Optional[JavaVersion]:
        required = self._get_required_java_version(mc_version)
        java_versions = self._get_installed_java_versions()

        for java in sorted(java_versions, key=lambda x: x.version):
            if java.version >= required:
                console.print(f"[cyan]Auto-selecting {java.display_name} for Minecraft {mc_version}[/cyan]")
                return java

        if self.selected_java:
            console.print(f"[yellow]Using user-selected {self.selected_java.display_name} for Minecraft {mc_version}[/yellow]")
            return self.selected_java

        console.print(f"[yellow]Using system default Java for Minecraft {mc_version}[/yellow]")
        return None

    def _display_header(self, title: str):
        console.clear()
        header = Panel(
            Align.center(Text(title, style="bold white")),
            box=box.ROUNDED, style="cyan", border_style="cyan"
        )
        console.print(header)
        console.print()

    def _display_menu(self):
        self._display_header("VISCORD BUILD MENU v1.0")

        table = Table(show_header=False, box=box.ROUNDED, border_style="cyan", pad_edge=False, width=60)
        table.add_column("Option", style="green", justify="center")
        table.add_column("Description", style="white")

        java_display = self.selected_java.display_name if self.selected_java else "System Default"

        menu_items = [
            ("[1]", "Build all versions → Releases folder"),
            ("[2]", "Build all versions → Versioned folders"),
            ("[3]", "Build specific version"),
            ("[4]", "Build to custom folder"),
            ("[5]", f"Select Java ({java_display})"),
            ("[6]", "Exit"),
        ]

        for opt, desc in menu_items:
            style = "red" if opt == "[6]" else ("yellow" if opt == "[5]" else "green")
            table.add_row(f"[{style}]{opt}[/{style}]", desc)

        console.print(Align.center(table))
        console.print()

        version_info = Panel(
            f"[cyan]Mod Version:[/cyan] [white]{self.mod_version}[/white]",
            box=box.ROUNDED, border_style="dim cyan"
        )
        console.print(Align.center(version_info))
        console.print()

    def _select_build_type(self) -> BuildType:
        self._display_header("SELECT BUILD TYPE")

        table = Table(show_header=False, box=box.ROUNDED, border_style="cyan")
        table.add_column("Option", style="green")
        table.add_column("Type", style="white bold")
        table.add_column("Description", style="dim white")
        table.add_row("[1]", "Clean Build", "Deletes previous build artifacts, rebuilds from scratch (recommended)")
        table.add_row("[2]", "Quick Build", "Builds only changed files, preserves existing artifacts (faster)")

        console.print(Align.center(table))
        console.print()

        choice = Prompt.ask("[cyan]Enter your choice[/cyan]", choices=["1", "2"], default="1")
        return BuildType.CLEAN if choice == "1" else BuildType.QUICK

    def _select_java_version(self):
        java_versions = self._get_installed_java_versions()

        if not java_versions:
            console.print("[red]No Java installations found![/red]")
            console.print("[yellow]Please install Java 21 to continue.[/yellow]")
            Prompt.ask("[cyan]Press Enter to continue...[/cyan]")
            return

        self._display_header("SELECT JAVA VERSION")

        table = Table(show_header=False, box=box.ROUNDED, border_style="cyan")
        table.add_column("Option", style="green")
        table.add_column("Version", style="white")

        for i, java in enumerate(java_versions, 1):
            table.add_row(f"[{i}]", java.display_name)
        table.add_row("[0]", "Use system default Java")

        console.print(Align.center(table))
        console.print()

        choices = [str(i) for i in range(len(java_versions) + 1)]
        choice = Prompt.ask("[cyan]Enter your choice[/cyan]", choices=choices, default="0")

        if choice == "0":
            self.selected_java = None
        else:
            self.selected_java = java_versions[int(choice) - 1]
            console.print(f"[green]Selected: {self.selected_java.display_name}[/green]")
            Prompt.ask("[cyan]Press Enter to continue...[/cyan]")

    def _get_log_path(self, mc_version: str, build_type: BuildType) -> Path:
        logs_dir = self.root_dir / "Logs"
        logs_dir.mkdir(exist_ok=True)
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        btype = "clean" if build_type == BuildType.CLEAN else "quick"
        return logs_dir / f"build-{mc_version}-{btype}-{stamp}.log"

    def _build_with_progress(self, project_name: str, mc_version: str, build_type: BuildType) -> BuildResult:
        status_text = "Clean Build" if build_type == BuildType.CLEAN else "Quick Build"
        log_path = self._get_log_path(mc_version, build_type)

        with Progress(
            SpinnerColumn(), TextColumn("[progress.description]{task.description}"),
            BarColumn(complete_style="green", finished_style="green"),
            TaskProgressColumn(), console=console, transient=False
        ) as progress:

            task = progress.add_task(f"[cyan]{status_text} - {project_name}...", total=100)
            output_file = tempfile.mktemp(suffix=".txt", prefix="gradle_output_")
            error_file  = tempfile.mktemp(suffix=".txt", prefix="gradle_error_")

            try:
                args = ["clean", "build"] if build_type == BuildType.CLEAN else ["build"]
                cmd = ["cmd.exe", "/c", "gradlew.bat"] + args

                with open(output_file, "w", encoding="utf-8", errors="replace") as out_f, \
                     open(error_file,  "w", encoding="utf-8", errors="replace") as err_f:
                    process = subprocess.Popen(cmd, stdout=out_f, stderr=err_f, shell=False)

                start_time = datetime.now()
                timeout = 600  # 10 minutes — some MC versions take a while on first run
                current_progress = 0

                # Task-name → progress milestone mapping
                TASK_PROGRESS = {
                    "clean": 10, "processResources": 20, "compileJava": 50,
                    "shadowJar": 70, "remapJar": 80, "jar": 85, "build": 95,
                }

                while process.poll() is None:
                    elapsed = (datetime.now() - start_time).total_seconds()
                    if elapsed > timeout:
                        process.terminate()
                        break

                    # Time-based floor so bar always moves even during silent tasks
                    time_progress = min(int(elapsed * 100 / timeout * 0.85), 85)
                    current_progress = max(current_progress, time_progress)

                    try:
                        with open(output_file, "r", encoding="utf-8", errors="ignore") as f:
                            lines = f.readlines()
                            if lines:
                                last_lines = "".join(lines[-15:])
                                # Pick up current Gradle task name for description
                                task_match = re.search(r"> Task :(?:\w+:)?(\w+)", last_lines)
                                if task_match:
                                    task_name = task_match.group(1)
                                    progress.update(task, description=f"[cyan]{status_text} — :{task_name}...")
                                    milestone = TASK_PROGRESS.get(task_name)
                                    if milestone:
                                        current_progress = max(current_progress, milestone)
                                if "BUILD SUCCESSFUL" in last_lines:
                                    current_progress = 100
                    except Exception:
                        pass

                    progress.update(task, completed=current_progress)
                    time.sleep(0.5)

                exit_code = process.returncode

                try:
                    with open(output_file, "r", encoding="utf-8", errors="ignore") as f:
                        output_lines = f.read().splitlines()
                except Exception:
                    output_lines = []

                try:
                    with open(error_file, "r", encoding="utf-8", errors="ignore") as f:
                        error_lines = f.read().splitlines()
                except Exception:
                    error_lines = []

                # Write combined log file
                try:
                    with open(log_path, "w", encoding="utf-8") as lf:
                        lf.write(f"=== Viscord Build Log ===\n")
                        lf.write(f"Version:    Minecraft {mc_version}\n")
                        lf.write(f"Build type: {status_text}\n")
                        lf.write(f"Started:    {datetime.now().isoformat()}\n")
                        lf.write(f"Exit code:  {exit_code}\n")
                        lf.write("=" * 60 + "\n\n")
                        lf.write("--- STDOUT ---\n")
                        lf.write("\n".join(output_lines))
                        lf.write("\n\n--- STDERR ---\n")
                        lf.write("\n".join(error_lines))
                except Exception:
                    log_path = None

                progress.update(task, completed=100,
                    description=f"[{'green' if exit_code == 0 else 'red'}]{status_text} {'Complete' if exit_code == 0 else 'FAILED'}![/{'green' if exit_code == 0 else 'red'}]")
                success = exit_code == 0 or any("BUILD SUCCESSFUL" in line for line in output_lines)

                return BuildResult(
                    success=success, output=output_lines, error=error_lines,
                    exit_code=exit_code, log_path=str(log_path) if log_path else None
                )

            except Exception as e:
                return BuildResult(success=False, output=[], error=[str(e)], exit_code=1)
            finally:
                for f in (output_file, error_file):
                    try:
                        os.unlink(f)
                    except Exception:
                        pass

    def _copy_jars(self, version: str, dest_dir: Path, rename: bool = True) -> bool:
        platforms = self.VERSION_PLATFORMS.get(version, [p for p in ["fabric", "forge", "neoforge"] if Path(p).exists()])
        platforms = [p for p in platforms if Path(p).exists()]

        if not platforms:
            console.print("[yellow]  - No platforms found[/yellow]")
            return False

        console.print(f"[cyan]  Available platforms: {', '.join(platforms)}[/cyan]")

        copied = False
        for platform in platforms:
            libs_path = Path(platform) / "build" / "libs"
            if not libs_path.exists():
                console.print(f"[yellow]  - {platform} build directory not found[/yellow]")
                continue

            jar_patterns = [
                f"viscord-{platform}-*.jar",
                f"viscord-*-{platform}.jar",
                f"viscord-{platform}.jar",
                f"viscord-*.jar",
            ]

            found = False
            for pattern in jar_patterns:
                jars = list(libs_path.glob(pattern))
                for jar in jars:
                    if any(x in jar.name for x in ["-dev-shadow", "-sources", "-transform"]):
                        continue

                    if rename:
                        new_name = f"viscord-{version}-{platform}-{self.mod_version}.jar"
                        dest_path = dest_dir / new_name
                    else:
                        dest_path = dest_dir / jar.name

                    shutil.copy2(jar, dest_path)
                    console.print(f"[green]  + {dest_path.name} copied[/green]")
                    found = True
                    copied = True

                if found:
                    break

            if not found:
                console.print(f"[yellow]  - No {platform} jars found[/yellow]")

        return copied

    def _get_version_dir(self, version: str) -> Optional[Path]:
        for item in self.root_dir.iterdir():
            if item.is_dir() and item.name.startswith(f"viscord-{version}-"):
                return item
        return None

    def _build_version(self, version: str, build_type: BuildType) -> BuildResult:
        ver_dir = self._get_version_dir(version)
        if not ver_dir:
            console.print(f"[red]X Could not find directory for version {version}[/red]")
            return BuildResult(False, [], [f"Directory not found for {version}"], 1)

        os.chdir(ver_dir)

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

        return result

    def _display_build_error(self, result: BuildResult):
        console.print(f"[red]Exit code: {result.exit_code}[/red]")

        # Extract the most useful lines: compiler errors and FAILED markers
        all_lines = result.output + result.error
        error_lines   = [l for l in all_lines if re.search(r"error:|FAILED|Exception|symbol:", l, re.IGNORECASE)]
        context_lines = result.output[-50:]  # last 50 lines of stdout for context

        if error_lines:
            console.print("[bold red]── Compiler / build errors ──[/bold red]")
            seen = set()
            for line in error_lines:
                stripped = line.strip()
                if stripped and stripped not in seen:
                    seen.add(stripped)
                    console.print(f"[red]  {stripped}[/red]")
            console.print()

        if context_lines:
            console.print("[dark_red]── Last 50 lines of build output ──[/dark_red]")
            for line in context_lines:
                stripped = line.strip()
                if not stripped:
                    continue
                # Highlight error/failed lines brighter, dim passing tasks
                if re.search(r"error:|FAILED|Exception", stripped, re.IGNORECASE):
                    console.print(f"[bold red]  {stripped}[/bold red]")
                elif re.search(r"BUILD SUCCESSFUL|UP-TO-DATE|NO-SOURCE", stripped):
                    console.print(f"[dim]  {stripped}[/dim]")
                else:
                    console.print(f"[red]  {stripped}[/red]")

        if result.log_path:
            console.print()
            console.print(f"[cyan]Full log saved → {result.log_path}[/cyan]")

    def build_all_releases(self):
        build_type = self._select_build_type()
        console.clear()
        self._display_header("BUILDING ALL VERSIONS → RELEASES")

        releases_dir = self.root_dir / "Releases"
        releases_dir.mkdir(exist_ok=True)
        console.print(f"[green]Created/Found Releases directory[/green]")
        console.print()

        total = len(self.VERSIONS)
        success_count = 0

        for i, version in enumerate(self.VERSIONS, 1):
            console.print(f"[yellow]Processing Minecraft {version}... ({i}/{total})[/yellow]")
            console.print("-" * 60)

            result = self._build_version(version, build_type)

            if result.success:
                console.print(f"[green]+ Build {version} successful. Copying jars...[/green]")
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, releases_dir, rename=True)
                os.chdir(self.root_dir)
                success_count += 1
            else:
                console.print(f"[red]X Failed to build {version}![/red]")
                self._display_build_error(result)

            console.print()

        self._display_summary(success_count, total, "Releases")

    def build_all_versioned(self):
        build_type = self._select_build_type()
        console.clear()
        self._display_header("BUILDING ALL VERSIONS → VERSIONED FOLDERS")

        total = len(self.VERSIONS)
        success_count = 0

        for version in self.VERSIONS:
            console.print(f"[yellow]Processing Minecraft {version}...[/yellow]")
            console.print("-" * 60)

            result = self._build_version(version, build_type)

            if result.success:
                console.print(f"[green]+ Build {version} successful. Creating folder...[/green]")
                version_dir = self.root_dir / version
                version_dir.mkdir(exist_ok=True)
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, version_dir, rename=False)
                os.chdir(self.root_dir)
                success_count += 1
            else:
                console.print(f"[red]X Failed to build {version}![/red]")
                self._display_build_error(result)

            console.print()

        self._display_summary(success_count, total, "versioned folders")

    def build_specific_version(self):
        self._display_header("BUILD SPECIFIC VERSION")

        table = Table(show_header=False, box=box.ROUNDED, border_style="cyan")
        table.add_column("Option", style="green")
        table.add_column("Version", style="white")

        for i, v in enumerate(self.VERSIONS, 1):
            table.add_row(f"[{i}]", f"Minecraft {v}")
        table.add_row("[5]", "Back to main menu")

        console.print(Align.center(table))
        console.print()

        choice = Prompt.ask("[cyan]Enter your choice[/cyan]", choices=["1", "2", "3", "4", "5"])
        if choice == "5":
            return

        version = self.VERSIONS[int(choice) - 1]
        build_type = self._select_build_type()
        result = self._build_version(version, build_type)

        console.clear()

        if result.success:
            self._display_header(f"✓ BUILT FOR MINECRAFT {version}")
            console.print(f"[green]+ Build {version} successful![/green]")
            console.print()
            console.print("[cyan]Where would you like to copy the jars?[/cyan]")
            console.print()

            options_table = Table(show_header=False, box=box.ROUNDED, border_style="cyan", width=60)
            options_table.add_column("Option", style="green", justify="center")
            options_table.add_column("Description", style="white")
            options_table.add_row("[1]", "Copy to Releases folder (with version names)")
            options_table.add_row("[2]", "Copy to version folder (raw names)")
            options_table.add_row("[3]", "Copy to custom folder")
            options_table.add_row("[4]", "Don't copy, return to menu")

            console.print(Align.center(options_table))
            console.print()

            dest_choice = Prompt.ask("[cyan]Choose destination[/cyan]", choices=["1", "2", "3", "4"], default="4")

            if dest_choice == "1":
                releases_dir = self.root_dir / "Releases"
                releases_dir.mkdir(exist_ok=True)
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, releases_dir, rename=True)
                os.chdir(self.root_dir)
                console.print(f"[green]+ Jars copied to Releases folder[/green]")
            elif dest_choice == "2":
                version_dir = self.root_dir / version
                version_dir.mkdir(exist_ok=True)
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, version_dir, rename=False)
                os.chdir(self.root_dir)
                console.print(f"[green]+ Jars copied to {version}/ folder[/green]")
            elif dest_choice == "3":
                custom_path = Prompt.ask("[cyan]Enter custom folder path[/cyan]")
                if custom_path.strip():
                    custom_dir = Path(custom_path.strip())
                    custom_dir.mkdir(parents=True, exist_ok=True)
                    os.chdir(self._get_version_dir(version))
                    self._copy_jars(version, custom_dir, rename=True)
                    os.chdir(self.root_dir)
                    console.print(f"[green]+ Jars copied to {custom_dir}[/green]")
                else:
                    console.print("[yellow]+ No path entered, jars not copied.[/yellow]")
            else:
                console.print("[yellow]+ Jars not copied.[/yellow]")
        else:
            self._display_header(f"✗ FAILED TO BUILD {version}")
            console.print(f"[red]X Build failed for {version}![/red]")
            console.print()
            self._display_build_error(result)

        console.print()
        Prompt.ask("[cyan]Press Enter to return to main menu...[/cyan]")

    def build_custom_folder(self):
        self._display_header("BUILD TO CUSTOM FOLDER")

        custom_folder = Prompt.ask("[cyan]Enter custom release folder name[/cyan]")
        if not custom_folder.strip():
            console.print("[red]Folder name cannot be empty.[/red]")
            Prompt.ask("[cyan]Press Enter to continue...[/cyan]")
            return self.build_custom_folder()

        build_type = self._select_build_type()
        console.clear()
        self._display_header(f"BUILDING TO CUSTOM FOLDER: {custom_folder}")

        custom_dir = self.root_dir / custom_folder
        custom_dir.mkdir(exist_ok=True)

        total = len(self.VERSIONS)
        success_count = 0

        for version in self.VERSIONS:
            console.print(f"[yellow]Processing Minecraft {version}...[/yellow]")

            result = self._build_version(version, build_type)

            if result.success:
                console.print(f"[green]+ Build {version} successful. Copying to {custom_folder}...[/green]")
                os.chdir(self._get_version_dir(version))
                self._copy_jars(version, custom_dir, rename=True)
                os.chdir(self.root_dir)
                success_count += 1
            else:
                console.print(f"[red]X Failed to build {version}![/red]")
                self._display_build_error(result)

            console.print()

        self._display_summary(success_count, total, custom_folder)

    def _display_summary(self, success_count: int, total_count: int, destination: str):
        console.clear()
        console.print()
        console.print(Panel(
            Align.center(Text("BUILD PROCESS COMPLETED", style="bold green")),
            box=box.ROUNDED, border_style="green"
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
        while True:
            self._display_menu()
            choice = Prompt.ask("[cyan]Enter your choice[/cyan]", choices=["1", "2", "3", "4", "5", "6"])

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
        console.clear()
        console.print(Panel(
            Align.center(Text("THANK YOU FOR USING\nVISCORD BUILD MENU v1.0", style="bold yellow")),
            box=box.ROUNDED, border_style="cyan"
        ))
        console.print(Align.center("[cyan]Have a great day![/cyan]"))
        time.sleep(1)


def main():
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
