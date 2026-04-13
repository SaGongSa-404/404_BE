const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("coupangApp", {
  extract(url) {
    return ipcRenderer.invoke("coupang:extract", url);
  },
});
