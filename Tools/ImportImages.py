import requests
import re
import os
import tkinter as tk
from tkinter import filedialog
import csv
from tkinter.ttk import Progressbar


def center(win):
    """
    centers a tkinter window
    :param win: the root or Toplevel window to center
    """
    win.update_idletasks()
    width = win.winfo_width()
    frm_width = win.winfo_rootx() - win.winfo_x()
    win_width = width + 2 * frm_width
    height = win.winfo_height()
    titlebar_height = win.winfo_rooty() - win.winfo_y()
    win_height = height + titlebar_height + frm_width
    x = win.winfo_screenwidth() // 2 - win_width // 2
    y = win.winfo_screenheight() // 2 - win_height // 2
    win.geometry('{}x{}+{}+{}'.format(width, height, x, y))
    win.deiconify()


def download_file_from_google_drive(id, destination, nb):
    URL = "https://docs.google.com/uc?export=download"

    session = requests.Session()

    response = session.get(URL, params={'id': id}, stream=True)
    token = get_confirm_token(response)

    if token:
        params = {'id': id, 'confirm': token}
        response = session.get(URL, params=params, stream=True)

    save_response_content(response, destination, nb)


def get_confirm_token(response):
    for key, value in response.cookies.items():
        if key.startswith('download_warning'):
            return value

    return None


def save_response_content(response, destination, nb):
    CHUNK_SIZE = 32768
    print(destination)
    fname = "image"
    cheminfic = destination + "/" + str(nb) + fname
    if not os.path.isfile(cheminfic):
        with open(cheminfic, "wb") as f:
            for chunk in response.iter_content(CHUNK_SIZE):
                if chunk:  # filter out keep-alive new chunks
                    f.write(chunk)


class Application(tk.Frame):
    def __init__(self, master=None):
        super().__init__(master)
        self.master = master
        self.pack()
        self.create_widgets()

    def create_widgets(self):
        self.winfo_toplevel().title("Importation images")
        self.parc = tk.Button(self, text='Dossier de destination', command=self.askopenfile)
        self.parc.grid(row=0, pady=10)

        self.var = tk.StringVar()
        self.lbl = tk.Label(self, textvariable=self.var)
        self.lbl.grid(row=2)
        self.quit = tk.Button(self, text="QUITTER", fg="red",
                              command=self.master.destroy)
        self.quit.grid(row=3, pady=10)

    def askopenfile(self):
        self.parc['state'] = 'disabled'
        destination = filedialog.askdirectory()
        if destination == '':
            self.parc['state'] = 'active'
        os.makedirs(destination, exist_ok=True)
        response = requests.get('https://docs.google.com/spreadsheets/d/e/2PACX-1vR3VlGjzLmkZ6hTu1q5-9nYEzVi1pZC0K9alvWwhV8aoTspoKFhjCaXpONlsRL4K2J0aMyF820k_Yr0/pub?output=csv')
        assert response.status_code == 200, 'Wrong status code'
        lines = response.content.decode("utf-8").splitlines()
        reader = csv.reader(lines)
        nblines = len(list(reader))
        print("nb dimage:" + str(nblines - 1))
        self.p = Progressbar(self, length=150, mode="determinate", maximum=nblines - 1)
        self.p.grid(row=1, pady=10)
        lines = response.content.decode("utf-8").splitlines()
        reader = csv.reader(lines)
        next(reader)
        nb = 0;
        for row in reader:
            print(row)
            self.var.set("Téléchargement de l'image " + str(nb) + "       Label : " + row[3])
            chemin = destination + '/' + row[3]
            os.makedirs(chemin, exist_ok=True)
            download_file_from_google_drive(row[4][-33:], chemin, nb)
            self.p.step()
            self.p.update()
            nb += 1
        self.var.set("Téléchargement Terminé")
        tk.messagebox.showinfo(title="Fin", message="Téléchargement des images terminé")


if __name__ == "__main__":
    root = tk.Tk()
    root.geometry('300x150')
    root.resizable(0, 0)
    center(root)

    app = Application(master=root)
    app.mainloop()
