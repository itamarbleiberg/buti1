# Third-party software in Player Characters

The mod jar includes the following, used for fart detection (offline speech recognition):

## Vosk speech recognition library

- `com.alphacephei:vosk` 0.3.45, from https://github.com/alphacep/vosk-api
- Copyright Alpha Cephei Inc.
- License: Apache License 2.0 (`META-INF/licenses/Apache-2.0.txt`)
- Packed unchanged inside the mod jar (`META-INF/jars/vosk-0.3.45.jar`). Its native libraries are built from Vosk,
  Kaldi and OpenFst (Apache License 2.0) and other libraries; see the Vosk project for details.

## Vosk small English model

- `vosk-model-small-en-us-0.15`, from https://alphacephei.com/vosk/models
- Copyright Alpha Cephei Inc.
- License: Apache License 2.0 (`META-INF/licenses/Apache-2.0.txt`)
- Changed for this mod: `graph/Gr.fst` (the language model for recognizing any English sentence) was left out to
  make the mod smaller, and `graph/words.txt` was added in its place. It is the word list that was stored inside
  `Gr.fst`. Everything else is unchanged. The build script (`build.gradle`, task `prepareSpeechModel`) does this.
