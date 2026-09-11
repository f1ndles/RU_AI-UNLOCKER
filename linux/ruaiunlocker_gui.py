#!/usr/bin/env python3

import os
import sys
import re
import json
import shutil
import datetime
import tempfile
import threading
import subprocess
import urllib.request
import urllib.error
import tkinter as tk
from tkinter import ttk, messagebox

CURRENT_VERSION = "1.0.0"
HOSTS_URL = "https://raw.githubusercontent.com/ImMALWARE/dns.malw.link/refs/heads/master/hosts"
GITHUB_RELEASES_API = "https://api.github.com/repos/f1ndles/RU_AI-UNLOCKER/releases/latest"
GITHUB_REPO_URL = "https://github.com/f1ndles/RU_AI-UNLOCKER/releases"

COLOR_BG = "#0F172A"
COLOR_CARD = "#1E293B"
COLOR_CARD_BORDER = "#334155"
COLOR_PURPLE = "#8B5CF6"
COLOR_PURPLE_HOVER = "#7C3AED"
COLOR_PURPLE_BANNER = "#2E1065"
COLOR_APP_BANNER = "#1E1B4B"
COLOR_TEXT = "#F8FAFC"
COLOR_TEXT_MUTED = "#94A3B8"
COLOR_GREEN = "#10B981"
COLOR_RED = "#EF4444"

def get_hosts_path():
    if os.name == "nt":
        return os.path.join(os.environ.get("SystemRoot", "C:\\Windows"), "System32", "drivers", "etc", "hosts")
    return "/etc/hosts"

def get_backup_dir():
    if os.name == "nt":
        base = os.environ.get("LOCALAPPDATA", os.path.expanduser("~"))
        return os.path.join(base, "RUAIUnlocker", "backups")
    return "/etc/ruaiunlocker/backups"

def read_hosts_content():
    path = get_hosts_path()
    if not os.path.exists(path):
        return ""
    try:
        with open(path, "r", encoding="utf-8", errors="ignore") as f:
            return f.read()
    except Exception as e:
        return ""

def write_hosts_file(content):
    path = get_hosts_path()
    try:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        return True
    except PermissionError:
        if os.name != "nt":
            try:
                with tempfile.NamedTemporaryFile("w", delete=False, encoding="utf-8") as tf:
                    tf.write(content)
                    tmp_name = tf.name
                cmd = ["pkexec", "cp", tmp_name, path]
                res = subprocess.run(cmd)
                try:
                    os.remove(tmp_name)
                except Exception:
                    pass
                return res.returncode == 0
            except Exception:
                return False
        return False
    except Exception:
        return False

def flush_dns_system():
    if os.name == "nt":
        try:
            subprocess.run(["ipconfig", "/flushdns"], capture_output=True)
            return True
        except Exception:
            return False
    else:
        success = False
        cmds = [
            ["resolvectl", "flush-caches"],
            ["systemd-resolve", "--flush-caches"],
            ["systemctl", "restart", "nscd"],
            ["systemctl", "restart", "systemd-resolved"]
        ]
        for cmd in cmds:
            try:
                res = subprocess.run(cmd, capture_output=True)
                if res.returncode == 0:
                    success = True
            except Exception:
                pass
        return success

def parse_hosts_date(content):
    if not content:
        return ""
    for line in content.splitlines():
        if "Последнее обновление" in line:
            parts = line.split(":", 1)
            if len(parts) > 1:
                return parts[1].strip()
    return ""

def parse_hosts_entries(content):
    entries = []
    current_section = "Системные"
    if not content:
        return entries

    for line in content.splitlines():
        s = line.strip()
        if not s:
            continue
        if s.startswith("#"):
            if "RU AI UNLOCKER" in s or "ImMALWARE" in s:
                current_section = "RU AI Unlocker"
            elif len(s) > 2 and not s.startswith("#!"):
                current_section = s.lstrip("#").strip()
            continue

        match = re.match(r"^([^\s#]+)\s+([^\s#]+)", s)
        if match:
            ip = match.group(1)
            domain = match.group(2)
            entries.append({
                "ip": ip,
                "domain": domain,
                "section": current_section,
                "raw": line
            })
    return entries

def create_hosts_backup():
    content = read_hosts_content()
    if not content:
        return None
    backup_dir = get_backup_dir()
    os.makedirs(backup_dir, exist_ok=True)
    ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    file_name = "hosts.bak." + ts
    full_path = os.path.join(backup_dir, file_name)
    try:
        with open(full_path, "w", encoding="utf-8") as f:
            f.write(content)
        return full_path
    except PermissionError:
        if os.name != "nt":
            try:
                with tempfile.NamedTemporaryFile("w", delete=False, encoding="utf-8") as tf:
                    tf.write(content)
                    tmp_name = tf.name
                subprocess.run(["pkexec", "cp", tmp_name, full_path])
                try:
                    os.remove(tmp_name)
                except Exception:
                    pass
                return full_path
            except Exception:
                return None
        return None
    except Exception:
        return None

def get_backups_list():
    backup_dir = get_backup_dir()
    if not os.path.exists(backup_dir):
        return []
    items = []
    try:
        for f in os.listdir(backup_dir):
            if f.startswith("hosts.bak."):
                p = os.path.join(backup_dir, f)
                stat = os.stat(p)
                size_str = f"{stat.st_size / 1024.0:.1f} KB" if stat.st_size > 1024 else f"{stat.st_size} B"
                dt_str = datetime.datetime.fromtimestamp(stat.st_mtime).strftime("%d.%m.%Y %H:%M:%S")
                items.append({
                    "name": f,
                    "path": p,
                    "date": dt_str,
                    "size": size_str,
                    "mtime": stat.st_mtime
                })
        items.sort(key=lambda x: x["mtime"], reverse=True)
    except Exception:
        pass
    return items

def check_hosts_remote():
    req = urllib.request.Request(HOSTS_URL, headers={"User-Agent": f"RUAIUnlocker-Linux/{CURRENT_VERSION}"})
    try:
        with urllib.request.urlopen(req, timeout=15) as res:
            data = res.read().decode("utf-8", errors="ignore")
            date = parse_hosts_date(data)
            return {"success": True, "content": data, "date": date}
    except Exception as e:
        return {"success": False, "error": str(e)}

def check_app_from_atom():
    req = urllib.request.Request("https://github.com/f1ndles/RU_AI-UNLOCKER/releases.atom", headers={"User-Agent": f"RUAIUnlocker-Linux/{CURRENT_VERSION}"})
    try:
        with urllib.request.urlopen(req, timeout=15) as res:
            xml_data = res.read().decode("utf-8", errors="ignore")
            import xml.etree.ElementTree as ET
            root = ET.fromstring(xml_data)
            ns = {"atom": "http://www.w3.org/2005/Atom"}
            entries = root.findall("atom:entry", ns)
            if not entries:
                return {"success": True, "has_update": False, "version": CURRENT_VERSION}
            candidates = []
            for entry in entries:
                id_elem = entry.find("atom:id", ns)
                title_elem = entry.find("atom:title", ns)
                content_elem = entry.find("atom:content", ns)
                link_elem = entry.find("atom:link", ns)
                tag = ""
                if id_elem is not None and id_elem.text:
                    tag = id_elem.text.split("/")[-1]
                ver = tag.lstrip("vV")
                title = title_elem.text if title_elem is not None and title_elem.text else tag
                content = content_elem.text if content_elem is not None and content_elem.text else ""
                clean_content = re.sub(r"<[^>]+>", "", content).strip()
                html_url = link_elem.get("href", GITHUB_REPO_URL) if link_elem is not None else GITHUB_REPO_URL
                candidates.append({
                    "tag": tag,
                    "version": ver,
                    "title": title,
                    "changelog": clean_content,
                    "html_url": html_url
                })
            for c in candidates:
                if is_newer_version(c["version"], CURRENT_VERSION):
                    candidate_urls = [
                        f"https://github.com/f1ndles/RU_AI-UNLOCKER/releases/download/{c['tag']}/ruaiunlocker.sh",
                        f"https://github.com/f1ndles/RU_AI-UNLOCKER/releases/download/{c['tag']}/ruaiunlocker_gui.py"
                    ]
                    found_url = ""
                    for u in candidate_urls:
                        try:
                            hreq = urllib.request.Request(u, headers={"User-Agent": f"RUAIUnlocker-Linux/{CURRENT_VERSION}"}, method="HEAD")
                            with urllib.request.urlopen(hreq, timeout=5) as hres:
                                if hres.status in (200, 302):
                                    found_url = u
                                    break
                        except Exception:
                            pass
                    if found_url:
                        return {
                            "success": True,
                            "has_update": True,
                            "version": c["version"],
                            "changelog": c["changelog"],
                            "download_url": found_url,
                            "html_url": c["html_url"]
                        }
            if candidates:
                for c in candidates:
                    if is_newer_version(c["version"], CURRENT_VERSION):
                        return {
                            "success": True,
                            "has_update": True,
                            "version": c["version"],
                            "changelog": c["changelog"],
                            "download_url": "",
                            "html_url": c["html_url"]
                        }
            return {"success": True, "has_update": False, "version": CURRENT_VERSION}
    except Exception as e:
        return {"success": False, "error": str(e)}

def check_app_remote():
    req = urllib.request.Request(GITHUB_RELEASES_API, headers={"User-Agent": f"RUAIUnlocker-Linux/{CURRENT_VERSION}"})
    try:
        with urllib.request.urlopen(req, timeout=15) as res:
            raw = res.read().decode("utf-8", errors="ignore")
            data = json.loads(raw)
            tag = data.get("tag_name", "").lstrip("vV")
            body = data.get("body", "")
            html_url = data.get("html_url", GITHUB_REPO_URL)
            download_url = ""
            for asset in data.get("assets", []):
                name = asset.get("name", "")
                if name.endswith(".sh") or "linux" in name.lower() or name.endswith(".py"):
                    download_url = asset.get("browser_download_url", "")
                    break
            has_update = is_newer_version(tag, CURRENT_VERSION)
            return {
                "success": True,
                "has_update": has_update,
                "version": tag,
                "changelog": body,
                "download_url": download_url,
                "html_url": html_url
            }
    except Exception:
        return check_app_from_atom()

def is_newer_version(remote, current):
    if not remote:
        return False
    try:
        r_parts = [int(p) for p in re.findall(r"\d+", remote)]
        c_parts = [int(p) for p in re.findall(r"\d+", current)]
        max_l = max(len(r_parts), len(c_parts))
        r_parts += [0] * (max_l - len(r_parts))
        c_parts += [0] * (max_l - len(c_parts))
        return r_parts > c_parts
    except Exception:
        return remote.strip() != current.strip()

class AddEntryDialog(tk.Toplevel):
    def __init__(self, parent, on_save):
        super().__init__(parent)
        self.title("Добавить запись Hosts")
        self.geometry("450x220")
        self.resizable(False, False)
        self.configure(bg=COLOR_BG)
        self.on_save = on_save

        self.transient(parent)
        self.grab_set()

        lbl_title = tk.Label(self, text="Новая запись в hosts", font=("sans-serif", 12, "bold"), bg=COLOR_BG, fg=COLOR_TEXT)
        lbl_title.pack(pady=(16, 12))

        frm_ip = tk.Frame(self, bg=COLOR_BG)
        frm_ip.pack(fill="x", padx=24, pady=4)
        tk.Label(frm_ip, text="IP-адрес:", width=12, anchor="w", bg=COLOR_BG, fg=COLOR_TEXT_MUTED, font=("sans-serif", 10)).pack(side="left")
        self.ent_ip = tk.Entry(frm_ip, bg=COLOR_CARD, fg=COLOR_TEXT, insertbackground=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 10))
        self.ent_ip.pack(side="right", fill="x", expand=True, ipady=4)
        self.ent_ip.insert(0, "127.0.0.1")

        frm_domain = tk.Frame(self, bg=COLOR_BG)
        frm_domain.pack(fill="x", padx=24, pady=4)
        tk.Label(frm_domain, text="Домен:", width=12, anchor="w", bg=COLOR_BG, fg=COLOR_TEXT_MUTED, font=("sans-serif", 10)).pack(side="left")
        self.ent_domain = tk.Entry(frm_domain, bg=COLOR_CARD, fg=COLOR_TEXT, insertbackground=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 10))
        self.ent_domain.pack(side="right", fill="x", expand=True, ipady=4)

        frm_btns = tk.Frame(self, bg=COLOR_BG)
        frm_btns.pack(fill="x", padx=24, pady=(20, 0))

        btn_cancel = tk.Button(frm_btns, text="Отмена", bg=COLOR_CARD, fg=COLOR_TEXT, relief="flat", highlightthickness=0, font=("sans-serif", 10), command=self.destroy, padx=12, pady=5)
        btn_cancel.pack(side="right", padx=(8, 0))

        btn_add = tk.Button(frm_btns, text="Добавить", bg=COLOR_PURPLE, fg="#FFFFFF", relief="flat", highlightthickness=0, font=("sans-serif", 10, "bold"), command=self.save_entry, padx=16, pady=5)
        btn_add.pack(side="right")

        self.ent_domain.focus_set()

    def save_entry(self):
        ip = self.ent_ip.get().strip()
        domain = self.ent_domain.get().strip()
        if not ip or not domain:
            messagebox.showwarning("Ошибка", "Заполните IP-адрес и домен.", parent=self)
            return
        self.on_save(ip, domain)
        self.destroy()

class RUAIUnlockerApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("RU AI Unlocker")
        self.geometry("960x700")
        self.minsize(820, 580)
        self.configure(bg=COLOR_BG)

        self.all_entries = []
        self.pending_hosts_content = None
        self.latest_app_release = None

        self.setup_styles()
        self.create_header()
        self.create_tabs()
        self.log("RU AI Unlocker (Linux Desktop) запущен")
        self.refresh_all_data()

        threading.Thread(target=self.check_updates_background, daemon=True).start()

    def setup_styles(self):
        self.style = ttk.Style(self)
        try:
            self.style.theme_use("clam")
        except Exception:
            pass

        self.style.configure("TNotebook", background=COLOR_BG, borderwidth=0)
        self.style.configure("TNotebook.Tab", background=COLOR_CARD, foreground=COLOR_TEXT_MUTED, padding=[16, 9], font=("sans-serif", 10, "bold"), borderwidth=0)
        self.style.map("TNotebook.Tab",
            background=[("selected", COLOR_PURPLE), ("active", "#2A374A")],
            foreground=[("selected", "#FFFFFF"), ("active", COLOR_TEXT)]
        )

        self.style.configure("Custom.Treeview",
            background=COLOR_CARD,
            foreground=COLOR_TEXT,
            fieldbackground=COLOR_CARD,
            rowheight=28,
            font=("sans-serif", 9),
            borderwidth=0
        )
        self.style.configure("Custom.Treeview.Heading",
            background=COLOR_BG,
            foreground="#A5B4FC",
            font=("sans-serif", 9, "bold"),
            borderwidth=1,
            relief="flat"
        )
        self.style.map("Custom.Treeview",
            background=[("selected", "#4338CA")],
            foreground=[("selected", "#FFFFFF")]
        )

        self.style.configure("Vertical.TScrollbar",
            background=COLOR_CARD,
            troughcolor=COLOR_BG,
            borderwidth=0,
            arrowsize=12
        )

    def create_header(self):
        hdr = tk.Frame(self, bg=COLOR_BG)
        hdr.pack(fill="x", padx=20, pady=(16, 12))

        left = tk.Frame(hdr, bg=COLOR_BG)
        left.pack(side="left")

        title_row = tk.Frame(left, bg=COLOR_BG)
        title_row.pack(anchor="w")

        lbl_title = tk.Label(title_row, text="RU AI Unlocker", font=("sans-serif", 18, "bold"), bg=COLOR_BG, fg=COLOR_TEXT)
        lbl_title.pack(side="left")

        lbl_platform = tk.Label(title_row, text=" Linux ", font=("sans-serif", 8, "bold"), bg=COLOR_PURPLE_BANNER, fg=COLOR_PURPLE, padx=6, pady=2)
        lbl_platform.pack(side="left", padx=(10, 4))

        lbl_ver = tk.Label(title_row, text=f" v{CURRENT_VERSION} ", font=("sans-serif", 8, "bold"), bg=COLOR_APP_BANNER, fg="#A5B4FC", padx=6, pady=2)
        lbl_ver.pack(side="left")

        lbl_desc = tk.Label(left, text="Управление и обновление hosts для разблокировки AI-сервисов", font=("sans-serif", 9), bg=COLOR_BG, fg=COLOR_TEXT_MUTED)
        lbl_desc.pack(anchor="w", pady=(4, 0))

        right = tk.Frame(hdr, bg=COLOR_BG)
        right.pack(side="right")

        btn_dns = tk.Button(right, text="Сбросить DNS", bg=COLOR_CARD, fg=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 9, "bold"), padx=12, pady=6, command=self.btn_flush_dns)
        btn_dns.pack(side="left", padx=(0, 8))

        btn_open = tk.Button(right, text="Открыть /etc/hosts", bg=COLOR_CARD, fg=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 9), padx=12, pady=6, command=self.btn_open_hosts)
        btn_open.pack(side="left")

    def create_tabs(self):
        self.notebook = ttk.Notebook(self)
        self.notebook.pack(fill="both", expand=True, padx=20, pady=(0, 16))

        self.tab_home = tk.Frame(self.notebook, bg=COLOR_BG)
        self.tab_hosts = tk.Frame(self.notebook, bg=COLOR_BG)
        self.tab_backups = tk.Frame(self.notebook, bg=COLOR_BG)
        self.tab_logs = tk.Frame(self.notebook, bg=COLOR_BG)

        self.notebook.add(self.tab_home, text=" Главная ")
        self.notebook.add(self.tab_hosts, text=" Записи Hosts ")
        self.notebook.add(self.tab_backups, text=" Бэкапы ")
        self.notebook.add(self.tab_logs, text=" Журнал ")

        self.build_tab_home()
        self.build_tab_hosts()
        self.build_tab_backups()
        self.build_tab_logs()

    def build_tab_home(self):
        scroll_frm = tk.Frame(self.tab_home, bg=COLOR_BG)
        scroll_frm.pack(fill="both", expand=True, pady=10)

        self.banner_container = tk.Frame(scroll_frm, bg=COLOR_BG)
        self.banner_container.pack(fill="x")

        self.banner_app = tk.Frame(self.banner_container, bg=COLOR_APP_BANNER, highlightbackground="#6366F1", highlightthickness=1)
        lbl_app_t = tk.Label(self.banner_app, text="Доступна новая версия программы!", font=("sans-serif", 11, "bold"), bg=COLOR_APP_BANNER, fg=COLOR_TEXT)
        lbl_app_t.pack(anchor="w", padx=16, pady=(12, 2))
        self.lbl_app_note = tk.Label(self.banner_app, text="Новый релиз на GitHub", font=("sans-serif", 9), bg=COLOR_APP_BANNER, fg="#C7D2FE", justify="left")
        self.lbl_app_note.pack(anchor="w", padx=16, pady=(0, 10))

        app_b_frm = tk.Frame(self.banner_app, bg=COLOR_APP_BANNER)
        app_b_frm.pack(anchor="e", padx=16, pady=(0, 12))
        btn_update_app = tk.Button(app_b_frm, text="Обновить программу", bg=COLOR_PURPLE, fg="#FFFFFF", relief="flat", font=("sans-serif", 9, "bold"), padx=12, pady=4, command=self.btn_update_app_click)
        btn_update_app.pack(side="left", padx=4)
        btn_app_gh = tk.Button(app_b_frm, text="GitHub", bg=COLOR_CARD, fg=COLOR_TEXT, relief="flat", font=("sans-serif", 9), padx=10, pady=4, command=self.btn_open_github)
        btn_app_gh.pack(side="left")

        self.banner_hosts = tk.Frame(self.banner_container, bg=COLOR_PURPLE_BANNER, highlightbackground=COLOR_PURPLE, highlightthickness=1)
        lbl_h_t = tk.Label(self.banner_hosts, text="Доступно обновление hosts!", font=("sans-serif", 11, "bold"), bg=COLOR_PURPLE_BANNER, fg=COLOR_TEXT)
        lbl_h_t.pack(anchor="w", padx=16, pady=(12, 2))
        self.lbl_hosts_note = tk.Label(self.banner_hosts, text="Новая версия от ...", font=("sans-serif", 9), bg=COLOR_PURPLE_BANNER, fg="#C4B5FD")
        self.lbl_hosts_note.pack(anchor="w", padx=16, pady=(0, 10))
        btn_update_h = tk.Button(self.banner_hosts, text="Обновить сейчас", bg=COLOR_PURPLE, fg="#FFFFFF", relief="flat", font=("sans-serif", 9, "bold"), padx=14, pady=4, command=self.btn_apply_hosts_update)
        btn_update_h.pack(anchor="e", padx=16, pady=(0, 12))

        cards_row = tk.Frame(scroll_frm, bg=COLOR_BG)
        cards_row.pack(fill="x", pady=(0, 14))

        self.card_ver = self.make_stat_card(cards_row, "Версия hosts", "Загрузка...", "Источник: dns.malw.link", COLOR_GREEN)
        self.card_count = self.make_stat_card(cards_row, "Активных записей", "0", "Разблокированных доменов", COLOR_PURPLE)
        self.card_size = self.make_stat_card(cards_row, "Размер файла hosts", "0 B", get_hosts_path(), COLOR_TEXT)

        act_box = tk.Frame(scroll_frm, bg=COLOR_CARD, highlightbackground=COLOR_CARD_BORDER, highlightthickness=1)
        act_box.pack(fill="x", pady=(0, 14), ipady=8)

        lbl_act = tk.Label(act_box, text="Быстрые действия", font=("sans-serif", 11, "bold"), bg=COLOR_CARD, fg=COLOR_TEXT)
        lbl_act.pack(anchor="w", padx=16, pady=(8, 12))

        wrap_frm = tk.Frame(act_box, bg=COLOR_CARD)
        wrap_frm.pack(fill="x", padx=12)

        tk.Button(wrap_frm, text="Проверить обновления hosts", bg=COLOR_PURPLE, fg="#FFFFFF", relief="flat", font=("sans-serif", 9, "bold"), padx=14, pady=8, command=self.btn_check_hosts_click).pack(side="left", padx=4, pady=4)
        tk.Button(wrap_frm, text="Проверить обновление программы", bg=COLOR_CARD, fg=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 9), padx=14, pady=8, command=self.btn_check_app_click).pack(side="left", padx=4, pady=4)
        tk.Button(wrap_frm, text="Обновить hosts из репозитория", bg=COLOR_PURPLE, fg="#FFFFFF", relief="flat", font=("sans-serif", 9, "bold"), padx=14, pady=8, command=self.btn_apply_hosts_update).pack(side="left", padx=4, pady=4)
        tk.Button(wrap_frm, text="Создать бэкап", bg=COLOR_CARD, fg=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 9), padx=14, pady=8, command=self.btn_create_backup_click).pack(side="left", padx=4, pady=4)
        tk.Button(wrap_frm, text="Сбросить кэш DNS", bg=COLOR_CARD, fg=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 9), padx=14, pady=8, command=self.btn_flush_dns).pack(side="left", padx=4, pady=4)

        about_box = tk.Frame(scroll_frm, bg=COLOR_CARD, highlightbackground=COLOR_CARD_BORDER, highlightthickness=1)
        about_box.pack(fill="x", ipady=6)

        lbl_ab_t = tk.Label(about_box, text="О модуле", font=("sans-serif", 10, "bold"), bg=COLOR_CARD, fg=COLOR_TEXT)
        lbl_ab_t.pack(anchor="w", padx=16, pady=(8, 4))

        lbl_ab_d = tk.Label(about_box, text="RU AI Unlocker разблокирует доступ к популярным AI сервисам (ChatGPT, Claude, Gemini и др.) через безопасный редирект хостов без замедления трафика.", font=("sans-serif", 9), bg=COLOR_CARD, fg=COLOR_TEXT_MUTED, wraplength=850, justify="left")
        lbl_ab_d.pack(anchor="w", padx=16)

        lbl_ab_f = tk.Label(about_box, text=f"RU AI Unlocker v{CURRENT_VERSION}  |  Репозиторий: github.com/f1ndles/RU_AI-UNLOCKER  |  Источник хостов: dns.malw.link", font=("sans-serif", 8), bg=COLOR_CARD, fg="#64748B")
        lbl_ab_f.pack(anchor="w", padx=16, pady=(6, 8))

    def make_stat_card(self, parent, title, val, sub, val_color):
        frm = tk.Frame(parent, bg=COLOR_CARD, highlightbackground=COLOR_CARD_BORDER, highlightthickness=1)
        frm.pack(side="left", fill="both", expand=True, padx=4, ipady=8)

        tk.Label(frm, text=title, font=("sans-serif", 9, "bold"), bg=COLOR_CARD, fg=COLOR_TEXT_MUTED).pack(anchor="w", padx=14, pady=(6, 2))
        lbl_v = tk.Label(frm, text=val, font=("sans-serif", 14, "bold"), bg=COLOR_CARD, fg=val_color)
        lbl_v.pack(anchor="w", padx=14)
        tk.Label(frm, text=sub, font=("sans-serif", 8), bg=COLOR_CARD, fg="#64748B").pack(anchor="w", padx=14, pady=(2, 6))
        return lbl_v

    def build_tab_hosts(self):
        top_bar = tk.Frame(self.tab_hosts, bg=COLOR_BG)
        top_bar.pack(fill="x", pady=(10, 8))

        lbl_s = tk.Label(top_bar, text="Поиск домена:", font=("sans-serif", 9, "bold"), bg=COLOR_BG, fg=COLOR_TEXT_MUTED)
        lbl_s.pack(side="left", padx=(0, 8))

        self.ent_search = tk.Entry(top_bar, bg=COLOR_CARD, fg=COLOR_TEXT, insertbackground=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 10))
        self.ent_search.pack(side="left", fill="x", expand=True, ipady=4)
        self.ent_search.bind("<KeyRelease>", lambda e: self.filter_entries())

        self.lbl_filter_stat = tk.Label(top_bar, text="Всего записей: 0", font=("sans-serif", 9), bg=COLOR_BG, fg=COLOR_TEXT_MUTED)
        self.lbl_filter_stat.pack(side="right", padx=(12, 0))

        tbl_frm = tk.Frame(self.tab_hosts, bg=COLOR_BG)
        tbl_frm.pack(fill="both", expand=True)

        cols = ("ip", "domain", "section")
        self.tree_hosts = ttk.Treeview(tbl_frm, columns=cols, show="headings", style="Custom.Treeview")
        self.tree_hosts.heading("ip", text="IP-адрес")
        self.tree_hosts.heading("domain", text="Домен")
        self.tree_hosts.heading("section", text="Раздел / Секция")

        self.tree_hosts.column("ip", width=180, anchor="w")
        self.tree_hosts.column("domain", width=420, anchor="w")
        self.tree_hosts.column("section", width=240, anchor="w")

        scrl = ttk.Scrollbar(tbl_frm, orient="vertical", command=self.tree_hosts.yview, style="Vertical.TScrollbar")
        self.tree_hosts.configure(yscrollcommand=scrl.set)

        self.tree_hosts.pack(side="left", fill="both", expand=True)
        scrl.pack(side="right", fill="y")

        btn_bar = tk.Frame(self.tab_hosts, bg=COLOR_BG)
        btn_bar.pack(fill="x", pady=(10, 0))

        btn_add = tk.Button(btn_bar, text="+ Добавить запись", bg=COLOR_PURPLE, fg="#FFFFFF", relief="flat", font=("sans-serif", 9, "bold"), padx=14, pady=6, command=self.btn_add_entry_dialog)
        btn_add.pack(side="left", padx=(0, 8))

        btn_del = tk.Button(btn_bar, text="Удалить выбранную", bg=COLOR_CARD, fg=COLOR_RED, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 9, "bold"), padx=12, pady=6, command=self.btn_delete_selected_entry)
        btn_del.pack(side="left", padx=(0, 8))

        btn_reload = tk.Button(btn_bar, text="Перезагрузить список", bg=COLOR_CARD, fg=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 9), padx=12, pady=6, command=self.refresh_all_data)
        btn_reload.pack(side="left")

    def build_tab_backups(self):
        top_bar = tk.Frame(self.tab_backups, bg=COLOR_BG)
        top_bar.pack(fill="x", pady=(10, 8))

        lbl_info = tk.Label(top_bar, text="Резервные копии hosts:", font=("sans-serif", 10, "bold"), bg=COLOR_BG, fg=COLOR_TEXT)
        lbl_info.pack(side="left")

        tbl_frm = tk.Frame(self.tab_backups, bg=COLOR_BG)
        tbl_frm.pack(fill="both", expand=True)

        cols = ("date", "name", "size")
        self.tree_backups = ttk.Treeview(tbl_frm, columns=cols, show="headings", style="Custom.Treeview")
        self.tree_backups.heading("date", text="Дата создания")
        self.tree_backups.heading("name", text="Имя файла бэкапа")
        self.tree_backups.heading("size", text="Размер")

        self.tree_backups.column("date", width=200, anchor="w")
        self.tree_backups.column("name", width=460, anchor="w")
        self.tree_backups.column("size", width=160, anchor="w")

        scrl = ttk.Scrollbar(tbl_frm, orient="vertical", command=self.tree_backups.yview, style="Vertical.TScrollbar")
        self.tree_backups.configure(yscrollcommand=scrl.set)

        self.tree_backups.pack(side="left", fill="both", expand=True)
        scrl.pack(side="right", fill="y")

        btn_bar = tk.Frame(self.tab_backups, bg=COLOR_BG)
        btn_bar.pack(fill="x", pady=(10, 0))

        btn_restore = tk.Button(btn_bar, text="Откатить hosts на выбранный бэкап", bg=COLOR_PURPLE, fg="#FFFFFF", relief="flat", font=("sans-serif", 9, "bold"), padx=14, pady=6, command=self.btn_restore_backup_click)
        btn_restore.pack(side="left", padx=(0, 8))

        btn_create = tk.Button(btn_bar, text="Создать бэкап сейчас", bg=COLOR_CARD, fg=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 9), padx=12, pady=6, command=self.btn_create_backup_click)
        btn_create.pack(side="left", padx=(0, 8))

        btn_open_f = tk.Button(btn_bar, text="Открыть папку бэкапов", bg=COLOR_CARD, fg=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 9), padx=12, pady=6, command=self.btn_open_backup_folder)
        btn_open_f.pack(side="left")

    def build_tab_logs(self):
        log_frm = tk.Frame(self.tab_logs, bg=COLOR_BG)
        log_frm.pack(fill="both", expand=True, pady=10)

        self.txt_logs = tk.Text(log_frm, bg=COLOR_CARD, fg=COLOR_TEXT, insertbackground=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("monospace", 9), wrap="word")
        scrl = ttk.Scrollbar(log_frm, orient="vertical", command=self.txt_logs.yview, style="Vertical.TScrollbar")
        self.txt_logs.configure(yscrollcommand=scrl.set)

        self.txt_logs.pack(side="left", fill="both", expand=True)
        scrl.pack(side="right", fill="y")

        btn_bar = tk.Frame(self.tab_logs, bg=COLOR_BG)
        btn_bar.pack(fill="x", pady=(4, 0))

        btn_copy = tk.Button(btn_bar, text="Скопировать журнал", bg=COLOR_CARD, fg=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 9), padx=12, pady=6, command=self.btn_copy_logs)
        btn_copy.pack(side="left", padx=(0, 8))

        btn_clear = tk.Button(btn_bar, text="Очистить", bg=COLOR_CARD, fg=COLOR_TEXT, relief="flat", highlightbackground=COLOR_CARD_BORDER, highlightthickness=1, font=("sans-serif", 9), padx=12, pady=6, command=self.btn_clear_logs)
        btn_clear.pack(side="left")

    def log(self, text):
        now = datetime.datetime.now().strftime("%H:%M:%S")
        msg = f"[{now}] {text}\n"
        self.txt_logs.insert("end", msg)
        self.txt_logs.see("end")

    def refresh_all_data(self):
        content = read_hosts_content()
        date = parse_hosts_date(content)
        self.all_entries = parse_hosts_entries(content)

        self.card_ver.config(text=date if date else "Не определена")
        self.card_count.config(text=str(len(self.all_entries)))

        sz = len(content.encode("utf-8"))
        if sz < 1024:
            sz_str = f"{sz} B"
        elif sz < 1024 * 1024:
            sz_str = f"{sz / 1024.0:.1f} KB"
        else:
            sz_str = f"{sz / (1024.0 * 1024.0):.1f} MB"
        self.card_size.config(text=sz_str)

        self.filter_entries()
        self.refresh_backups_table()

    def filter_entries(self):
        q = self.ent_search.get().strip().lower()
        for row in self.tree_hosts.get_children():
            self.tree_hosts.delete(row)

        count = 0
        for e in self.all_entries:
            if not q or q in e["domain"].lower() or q in e["ip"]:
                self.tree_hosts.insert("", "end", values=(e["ip"], e["domain"], e["section"]))
                count += 1

        self.lbl_filter_stat.config(text=f"Всего: {len(self.all_entries)} | Найдено: {count}")

    def refresh_backups_table(self):
        for row in self.tree_backups.get_children():
            self.tree_backups.delete(row)

        backups = get_backups_list()
        for b in backups:
            self.tree_backups.insert("", "end", iid=b["path"], values=(b["date"], b["name"], b["size"]))

    def check_updates_background(self):
        self.log("Фоновая проверка обновлений...")
        app_res = check_app_remote()
        if app_res.get("success") and app_res.get("has_update"):
            self.latest_app_release = app_res
            self.after(0, lambda: self.show_app_banner(app_res["version"], app_res["changelog"]))
            self.log(f"Найдена новая версия программы: v{app_res['version']}")

        hosts_res = check_hosts_remote()
        if hosts_res.get("success"):
            curr_content = read_hosts_content()
            curr_date = parse_hosts_date(curr_content)
            remote_date = hosts_res.get("date", "")
            if remote_date and remote_date != curr_date:
                self.pending_hosts_content = hosts_res["content"]
                self.after(0, lambda: self.show_hosts_banner(remote_date))
                self.log(f"Найдено обновление hosts! Текущая: '{curr_date}', Доступна: '{remote_date}'")
            else:
                self.log(f"Hosts актуален ({curr_date})")

    def show_app_banner(self, ver, note):
        clean_note = note.strip() if note else "Обновление на GitHub"
        if len(clean_note) > 140:
            clean_note = clean_note[:137] + "..."
        self.lbl_app_note.config(text=f"Версия v{ver}: {clean_note}")
        self.banner_app.pack(fill="x", pady=(0, 12))

    def show_hosts_banner(self, date):
        self.lbl_hosts_note.config(text=f"Доступна новая база хостов от {date}")
        self.banner_hosts.pack(fill="x", pady=(0, 12))

    def btn_flush_dns(self):
        self.log("Сброс кэша DNS...")
        if flush_dns_system():
            self.log("Кэш DNS успешно сброшен.")
            messagebox.showinfo("DNS", "Кэш DNS успешно сброшен!", parent=self)
        else:
            self.log("Не удалось сбросить кэш DNS автоматически.")
            messagebox.showwarning("DNS", "Не удалось сбросить кэш DNS.", parent=self)

    def btn_open_hosts(self):
        path = get_hosts_path()
        try:
            if os.name == "nt":
                os.startfile(path)
            else:
                subprocess.Popen(["xdg-open", path])
        except Exception as e:
            messagebox.showerror("Ошибка", f"Не удалось открыть файл: {e}", parent=self)

    def btn_check_hosts_click(self):
        self.log("Ручная проверка обновления hosts...")
        res = check_hosts_remote()
        if not res.get("success"):
            messagebox.showerror("Ошибка", f"Не удалось проверить hosts: {res.get('error')}", parent=self)
            return

        curr_content = read_hosts_content()
        curr_date = parse_hosts_date(curr_content)
        remote_date = res.get("date", "")
        if remote_date and remote_date != curr_date:
            self.pending_hosts_content = res["content"]
            self.show_hosts_banner(remote_date)
            messagebox.showinfo("Обновление hosts", f"Найдено обновление hosts от {remote_date}!", parent=self)
        else:
            messagebox.showinfo("Обновление hosts", f"У вас установлена актуальная версия hosts ({curr_date}).", parent=self)

    def btn_check_app_click(self):
        self.log("Ручная проверка обновлений программы...")
        res = check_app_remote()
        if not res.get("success"):
            messagebox.showerror("Ошибка", f"Не удалось проверить обновления: {res.get('error')}", parent=self)
            return

        if res.get("has_update"):
            self.latest_app_release = res
            self.show_app_banner(res["version"], res["changelog"])
            messagebox.showinfo("Обновление программы", f"Найдена новая версия v{res['version']}!\n\nЧто нового:\n{res['changelog']}", parent=self)
        else:
            messagebox.showinfo("Обновление программы", f"У вас установлена актуальная версия программы (v{CURRENT_VERSION}).", parent=self)

    def btn_apply_hosts_update(self):
        content = self.pending_hosts_content
        if not content:
            res = check_hosts_remote()
            if res.get("success"):
                content = res["content"]
            else:
                messagebox.showerror("Ошибка", "Не удалось скачать hosts.", parent=self)
                return

        bak = create_hosts_backup()
        if bak:
            self.log(f"Создан бэкап: {bak}")

        if write_hosts_file(content):
            self.banner_hosts.pack_forget()
            flush_dns_system()
            self.refresh_all_data()
            new_date = parse_hosts_date(content)
            self.log(f"Hosts успешно обновлен до версии от {new_date}")
            messagebox.showinfo("Успех", f"Hosts успешно обновлен до версии от {new_date}!\nКэш DNS сброшен.", parent=self)
        else:
            messagebox.showerror("Ошибка", "Не удалось записать файл /etc/hosts.\nУбедитесь, что у вас есть права root/sudo.", parent=self)

    def btn_create_backup_click(self):
        b = create_hosts_backup()
        if b:
            self.log(f"Создан бэкап: {b}")
            self.refresh_backups_table()
            messagebox.showinfo("Бэкап", f"Бэкап успешно сохранён:\n{b}", parent=self)
        else:
            messagebox.showerror("Ошибка", "Не удалось создать бэкап.", parent=self)

    def btn_restore_backup_click(self):
        selected = self.tree_backups.selection()
        if not selected:
            messagebox.showwarning("Внимание", "Выберите бэкап из списка для отката.", parent=self)
            return
        b_path = selected[0]
        if not os.path.exists(b_path):
            messagebox.showerror("Ошибка", "Файл бэкапа не найден.", parent=self)
            return

        if not messagebox.askyesno("Подтверждение", f"Откатить hosts на этот бэкап?\n{b_path}", parent=self):
            return

        try:
            with open(b_path, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
            if write_hosts_file(content):
                flush_dns_system()
                self.refresh_all_data()
                self.log(f"Hosts успешно восстановлен из {b_path}")
                messagebox.showinfo("Успех", "Hosts успешно восстановлен из бэкапа!\nКэш DNS сброшен.", parent=self)
            else:
                messagebox.showerror("Ошибка", "Не удалось восстановить hosts.", parent=self)
        except Exception as e:
            messagebox.showerror("Ошибка", str(e), parent=self)

    def btn_open_backup_folder(self):
        d = get_backup_dir()
        os.makedirs(d, exist_ok=True)
        try:
            if os.name == "nt":
                os.startfile(d)
            else:
                subprocess.Popen(["xdg-open", d])
        except Exception as e:
            messagebox.showerror("Ошибка", str(e), parent=self)

    def btn_add_entry_dialog(self):
        AddEntryDialog(self, self.add_custom_entry)

    def add_custom_entry(self, ip, domain):
        content = read_hosts_content()
        create_hosts_backup()
        new_content = content.rstrip() + f"\n{ip}\t{domain}\n"
        if write_hosts_file(new_content):
            flush_dns_system()
            self.refresh_all_data()
            self.log(f"Добавлена запись: {ip} {domain}")
            messagebox.showinfo("Успех", f"Запись {ip} {domain} успешно добавлена!", parent=self)
        else:
            messagebox.showerror("Ошибка", "Не удалось записать в hosts.", parent=self)

    def btn_delete_selected_entry(self):
        selected = self.tree_hosts.selection()
        if not selected:
            messagebox.showwarning("Внимание", "Выберите запись для удаления.", parent=self)
            return
        item = self.tree_hosts.item(selected[0])
        vals = item.get("values", [])
        if not vals or len(vals) < 2:
            return
        ip = str(vals[0])
        domain = str(vals[1])

        if not messagebox.askyesno("Удаление", f"Удалить запись '{ip} {domain}' из hosts?", parent=self):
            return

        create_hosts_backup()
        content = read_hosts_content()
        new_lines = []
        deleted = False
        for line in content.splitlines():
            s = line.strip()
            if not deleted and not s.startswith("#"):
                match = re.match(r"^([^\s#]+)\s+([^\s#]+)", s)
                if match and match.group(1) == ip and match.group(2) == domain:
                    deleted = True
                    continue
            new_lines.append(line)

        new_content = "\n".join(new_lines) + "\n"
        if write_hosts_file(new_content):
            flush_dns_system()
            self.refresh_all_data()
            self.log(f"Удалена запись: {ip} {domain}")
            messagebox.showinfo("Успех", f"Запись {ip} {domain} успешно удалена!", parent=self)
        else:
            messagebox.showerror("Ошибка", "Не удалось обновить hosts.", parent=self)

    def btn_update_app_click(self):
        if not self.latest_app_release:
            return
        url = self.latest_app_release.get("download_url")
        if not url:
            self.btn_open_github()
            return

        if not messagebox.askyesno("Обновление", f"Скачать и обновить программу до версии v{self.latest_app_release['version']}?", parent=self):
            return

        self.log(f"Скачивание обновления с {url}...")
        try:
            curr_script = os.path.abspath(sys.argv[0])
            script_dir = os.path.dirname(curr_script)
            target = os.path.join(script_dir, "ruaiunlocker.sh") if url.endswith(".sh") else curr_script
            with urllib.request.urlopen(url, timeout=30) as res:
                data = res.read()
            with open(target, "wb") as f:
                f.write(data)
            try:
                os.chmod(target, 0o755)
            except Exception:
                pass
            self.log("Программа успешно обновлена! Перезапустите приложение.")
            messagebox.showinfo("Успех", "Программа успешно обновлена! Перезапустите приложение.", parent=self)
        except Exception as e:
            self.log(f"Ошибка обновления: {e}")
            messagebox.showerror("Ошибка", f"Не удалось обновить: {e}\n\nВы можете скачать обновление вручную с GitHub.", parent=self)
            self.btn_open_github()

    def btn_open_github(self):
        url = GITHUB_REPO_URL
        if self.latest_app_release and self.latest_app_release.get("html_url"):
            url = self.latest_app_release["html_url"]
        try:
            if os.name == "nt":
                os.startfile(url)
            else:
                subprocess.Popen(["xdg-open", url])
        except Exception:
            pass

    def btn_copy_logs(self):
        text = self.txt_logs.get("1.0", "end-1c")
        self.clipboard_clear()
        self.clipboard_append(text)
        messagebox.showinfo("Журнал", "Логи скопированы в буфер обмена.", parent=self)

    def btn_clear_logs(self):
        self.txt_logs.delete("1.0", "end")

if __name__ == "__main__":
    app = RUAIUnlockerApp()
    app.mainloop()
