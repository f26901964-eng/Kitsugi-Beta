import os
import io
import sys
import time
import threading
import subprocess
import urllib.request
from PIL import Image, ImageTk
import customtkinter as ctk

# Configure CustomTkinter
ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("blue")

class PhoneWebcamApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        
        self.title("Xiaomi 13 Ultra - ADB Webcam Streamer")
        self.geometry("800x650")
        self.resizable(True, True)
        
        self.adb_path = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
        if not os.path.exists(self.adb_path):
            self.adb_path = 'adb' # Fallback
            
        self.streaming = False
        self.stream_thread = None
        self.bytes_data = b''
        
        self.create_widgets()
        self.check_adb_connection()

    def create_widgets(self):
        # Main Layout
        self.grid_rowconfigure(1, weight=1)
        self.grid_columnconfigure(0, weight=1)
        
        # Header / Control Panel
        self.control_frame = ctk.CTkFrame(self, corner_radius=10)
        self.control_frame.grid(row=0, column=0, padx=15, pady=10, sticky="ew")
        
        self.status_label = ctk.CTkLabel(
            self.control_frame, 
            text="Durum: Cihaz kontrol ediliyor...", 
            font=ctk.CTkFont(size=14, weight="bold")
        )
        self.status_label.grid(row=0, column=0, padx=15, pady=10, sticky="w")
        
        self.btn_forward = ctk.CTkButton(
            self.control_frame, 
            text="ADB Port Yönlendir (4747)", 
            command=self.forward_port
        )
        self.btn_forward.grid(row=0, column=1, padx=10, pady=10)
        
        self.btn_start = ctk.CTkButton(
            self.control_frame, 
            text="Yayını Başlat", 
            fg_color="green", 
            hover_color="darkgreen",
            command=self.toggle_stream
        )
        self.btn_start.grid(row=0, column=2, padx=10, pady=10)
        
        self.btn_screenshot = ctk.CTkButton(
            self.control_frame, 
            text="Fotoğraf Çek", 
            state="disabled",
            command=self.take_screenshot
        )
        self.btn_screenshot.grid(row=0, column=3, padx=10, pady=10)
        
        # Video Display Panel
        self.video_frame = ctk.CTkFrame(self, corner_radius=10, fg_color="black")
        self.video_frame.grid(row=1, column=0, padx=15, pady=10, sticky="nsew")
        self.video_frame.grid_rowconfigure(0, weight=1)
        self.video_frame.grid_columnconfigure(0, weight=1)
        
        self.video_label = ctk.CTkLabel(self.video_frame, text="Yayın Yok", text_color="gray")
        self.video_label.grid(row=0, column=0, sticky="nsew")

    def run_adb(self, args):
        try:
            result = subprocess.run([self.adb_path] + args, capture_output=True, text=True, check=True)
            return result.stdout.strip()
        except Exception as e:
            return f"Hata: {e}"

    def check_adb_connection(self):
        devices = self.run_adb(["devices"])
        if "device" in devices and len(devices.split("\n")) > 1:
            self.status_label.configure(text="Durum: Xiaomi 13 Ultra Bağlı (ADB)", text_color="lightgreen")
        else:
            self.status_label.configure(text="Durum: Cihaz Bulunamadı! USB Hata Ayıklamayı Açın", text_color="red")
        self.after(5000, self.check_adb_connection)

    def forward_port(self):
        res = self.run_adb(["forward", "tcp:4747", "tcp:4747"])
        if "Hata" in res:
            self.status_label.configure(text=f"Yönlendirme Hatası: {res}", text_color="red")
        else:
            self.status_label.configure(text="Port 4747 Başarıyla Yönlendirildi", text_color="cyan")

    def toggle_stream(self):
        if self.streaming:
            self.streaming = False
            self.btn_start.configure(text="Yayını Başlat", fg_color="green", hover_color="darkgreen")
            self.btn_screenshot.configure(state="disabled")
            self.video_label.configure(text="Yayın Durduruldu", text_color="gray")
        else:
            # Auto-forward port just in case
            self.forward_port()
            self.streaming = True
            self.btn_start.configure(text="Yayını Durdur", fg_color="red", hover_color="darkred")
            self.btn_screenshot.configure(state="normal")
            self.video_label.configure(text="Bağlanıyor...", text_color="yellow")
            
            self.bytes_data = b''
            self.stream_thread = threading.Thread(target=self.get_stream, daemon=True)
            self.stream_thread.start()

    def get_stream(self):
        url = "http://localhost:4747/video"
        try:
            stream = urllib.request.urlopen(url, timeout=5)
            while self.streaming:
                chunk = stream.read(4096)
                if not chunk:
                    break
                self.bytes_data += chunk
                
                # Check for buffer limit
                if len(self.bytes_data) > 10 * 1024 * 1024:
                    self.bytes_data = b''
                    continue
                
                a = self.bytes_data.find(b'\xff\xd8')
                b = self.bytes_data.find(b'\xff\xd9')
                
                if a != -1 and b != -1:
                    if a < b:
                        jpg = self.bytes_data[a:b+2]
                        self.bytes_data = self.bytes_data[b+2:]
                        self.update_image(jpg)
                    else:
                        self.bytes_data = self.bytes_data[a:]
        except Exception as e:
            self.streaming = False
            self.after(0, lambda: self.video_label.configure(text=f"Yayın Bağlantı Hatası: {e}", text_color="red"))
            self.after(0, lambda: self.btn_start.configure(text="Yayını Başlat", fg_color="green", hover_color="darkgreen"))
            self.after(0, lambda: self.btn_screenshot.configure(state="disabled"))

    def update_image(self, jpg_data):
        try:
            image = Image.open(io.BytesIO(jpg_data))
            
            # Get current size of the video label to resize dynamically
            width = self.video_label.winfo_width()
            height = self.video_label.winfo_height()
            
            if width > 10 and height > 10:
                image = image.resize((width, height), Image.Resampling.LANCZOS)
                
            self.current_img = ImageTk.PhotoImage(image)
            self.after(0, lambda: self.video_label.configure(image=self.current_img, text=""))
        except Exception:
            pass

    def take_screenshot(self):
        if hasattr(self, 'current_img'):
            try:
                # Re-fetch the last clean image data if possible
                filename = f"capture_{int(time.time())}.jpg"
                # Just save the current displayed image size
                img_to_save = ImageTk.getimage(self.current_img)
                img_to_save.convert("RGB").save(filename, "JPEG")
                self.status_label.configure(text=f"Fotoğraf kaydedildi: {filename}", text_color="lightgreen")
            except Exception as e:
                self.status_label.configure(text=f"Fotoğraf kaydedilemedi: {e}", text_color="red")

if __name__ == "__main__":
    app = PhoneWebcamApp()
    app.mainloop()
