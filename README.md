# myIPTV

**myIPTV** è un'app per guardare i canali TV della tua playlist IPTV su **Android TV** e **Google TV**
(per esempio *Chromecast con Google TV* e *Google TV Streamer*).

È pensata per essere usata comodamente con il telecomando: niente menu complicati, solo i tuoi canali,
in ordine, con la guida TV sempre a portata di mano.

---

## Cosa sa fare

- 📺 **Le tue playlist** – aggiungi una playlist da un indirizzo web (URL) oppure da un file `.m3u`
  salvato sul dispositivo o su una chiavetta USB. Puoi salvarne più di una e passare dall'una all'altra.
- 🔢 **Canali in ordine di numero** – i canali sono mostrati con la loro numerazione (LCN), come su un televisore.
- ⬆️⬇️ **Cambio canale con le frecce** – durante la visione, premi su e giù per passare al canale successivo o precedente.
- 🗓️ **Guida TV (EPG)** – vedi cosa va in onda adesso e cosa c'è dopo, oppure sfoglia la guida completa a griglia
  mentre il canale che stavi guardando continua in un riquadro in alto a sinistra.
- 👀 **Anteprima dal vivo** – scorrendo la lista, il canale selezionato parte in anteprima nel riquadro in alto a destra.
- 📊 **Informazioni sullo streaming** – risoluzione, formato video e audio, velocità della connessione e altro.
- 🔊 **Più tracce audio e sottotitoli** – se un canale trasmette in più lingue puoi scegliere quella che preferisci.
- ⭐ **Preferiti** – tieni premuto OK su un canale per aggiungerlo ai preferiti.
- 🎨 **Grafica moderna** – stile *Material You*, con tema scuro o chiaro e colori che puoi personalizzare.
- 🔄 **Aggiornamenti automatici** – l'app ti avvisa quando esce una nuova versione e si aggiorna da sola.
- 🌍 **Italiano e inglese** – l'app segue la lingua del dispositivo, ma puoi cambiarla dalle impostazioni.

---

## Primo avvio

1. Apri myIPTV e premi **Aggiungi playlist**.
2. Scegli come aggiungerla:
   - **Indirizzo web (URL)**: scrivi l'indirizzo che ti ha dato il tuo fornitore.
   - **File sul dispositivo**: scegli un file `.m3u` dalla memoria del dispositivo o da una chiavetta USB.
3. *(Facoltativo)* inserisci l'indirizzo della **guida TV (EPG)**. Se la tua playlist ne contiene già uno, puoi lasciare il campo vuoto.
4. Premi **Salva**: i canali compariranno in pochi secondi.

---

## Come si usa con il telecomando

### Nella lista dei canali

| Tasto | Cosa fa |
|---|---|
| ⬆️ ⬇️ | Scorri i canali |
| ⬅️ | Vai al menu: Guida TV, Impostazioni e **Gruppi** (premi OK per aprire o chiudere l'elenco dei gruppi) |
| *(fermati su un canale)* | Parte l'anteprima in alto a destra |
| **OK** | Guarda il canale a schermo intero |
| **OK tenuto premuto** | Aggiungi o togli dai preferiti |
| **Indietro** | Esci dall'app |

### Mentre guardi un canale

| Tasto | Cosa fa |
|---|---|
| ⬆️ / ⬇️ | Canale successivo / precedente |
| **OK** | Mostra cosa va in onda e i comandi: **Guida TV** e **Info** (descrizione del programma) a sinistra, **Audio**, **Sottotitoli** e **Formato** a destra (scegli con ⬅️ ➡️ e OK) |
| ⬅️ | Apri la lista dei canali (con ⬅️ ➡️ cambi gruppo) |
| ➡️ | Mostra o nasconde le informazioni sullo streaming (risoluzione, formato, velocità…) |
| Tasti numerici | Vai direttamente al canale con quel numero (se il telecomando li ha) |
| **Indietro** | Torna alla lista dei canali |

> Preferisci che "su" vada al canale precedente? Puoi invertire le frecce nelle **Impostazioni**.

### Nella Guida TV

| Tasto | Cosa fa |
|---|---|
| ⬆️ ⬇️ | Scorri i canali |
| ⬅️ ➡️ | Scorri i programmi avanti e indietro nel tempo |
| **OK** | Guarda il canale a schermo intero |
| **Indietro** | Chiudi la guida |

### Formati immagine

- **Adatta**: l'immagine originale, senza tagli.
- **Allarga**: allarga l'immagine in orizzontale, utile per i programmi in 4:3 con le bande nere ai lati.
- **Zoom**: ingrandisce l'immagine tagliando i bordi, utile per film con le bande nere sopra e sotto.

---

## Impostazioni utili

- **Playlist**: aggiungi, modifica, aggiorna o elimina le tue playlist e scegli quella da usare.
- **Avvia sull'ultimo canale**: all'apertura l'app riparte dall'ultimo canale che stavi guardando.
- **Lingua audio preferita**: l'app sceglie in automatico l'audio nella lingua che preferisci, quando disponibile.
- **Aspetto** (scuro o chiaro), **Colore del tema** e **Lingua** dell'app.
- **Aggiornamenti**: controlla se c'è una nuova versione.

La prima volta che installi un aggiornamento, il televisore ti chiederà di consentire a myIPTV di installare app:
basta attivare l'interruttore e tornare indietro.

---

## Domande frequenti

**Non vedo la guida TV.**
Controlla che la playlist contenga l'indirizzo della guida o inseriscilo tu modificando la playlist.
La guida viene aggiornata automaticamente ogni 12 ore; puoi forzare l'aggiornamento da *Impostazioni → Aggiorna playlist e guida TV*.

**Un canale non parte.**
Premi OK per riprovare. Se il problema continua, il canale potrebbe non essere disponibile in quel momento
oppure essere protetto da sistemi che l'app non può riprodurre.

**Non riesco a scegliere un file dalla memoria.**
Alcuni dispositivi Google TV non hanno il selettore file di sistema: usa il pulsante **Sfoglia la memoria**
e concedi il permesso di accesso ai file quando richiesto.

**L'app fornisce dei canali?**
No. myIPTV è solo un lettore: i canali provengono dalla playlist che inserisci tu.

---

Per chi vuole contribuire o compilare l'app da sé, le istruzioni tecniche sono in [docs/SVILUPPO.md](docs/SVILUPPO.md).
