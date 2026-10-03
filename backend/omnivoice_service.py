import io
import os
import threading
from dataclasses import dataclass
from typing import Any


class OmniVoiceError(RuntimeError):
    pass


@dataclass
class OmniVoiceConfig:
    model_name: str
    device_map: str
    dtype: str
    ref_audio: str
    ref_text: str
    language_id: str | None
    speed: float
    num_step: int


class OmniVoiceService:
    def __init__(self) -> None:
        self._model: Any | None = None
        self._lock = threading.Lock()

    def synthesize(self, text: str) -> bytes:
        with self._lock:
            model = self._get_model()
            config = self._config()
            generation_config = self._generation_config(config.num_step)
            kwargs: dict[str, Any] = {
                "text": text,
                "speed": config.speed,
                "generation_config": generation_config,
            }
            if config.ref_audio:
                kwargs["ref_audio"] = config.ref_audio
            if config.ref_text:
                kwargs["ref_text"] = config.ref_text
            if config.language_id:
                kwargs["language"] = config.language_id

            try:
                audio = model.generate(**kwargs)
            except Exception as exc:
                raise OmniVoiceError(f"OmniVoice generation failed: {exc}") from exc

            if not audio:
                raise OmniVoiceError("OmniVoice returned empty audio.")

            return self._wav_bytes(audio[0])

    def _get_model(self) -> Any:
        if self._model is not None:
            return self._model

        config = self._config()
        try:
            import torch
            from omnivoice import OmniVoice
        except Exception as exc:
            raise OmniVoiceError(
                "OmniVoice is not installed. Install it in the backend environment with "
                "`pip install omnivoice` or `pip install git+https://github.com/k2-fsa/OmniVoice.git`."
            ) from exc

        dtype = self._resolve_dtype(torch, config.dtype)
        try:
            self._model = OmniVoice.from_pretrained(
                config.model_name,
                device_map=config.device_map,
                dtype=dtype,
            )
        except Exception as exc:
            raise OmniVoiceError(f"Failed to load OmniVoice model: {exc}") from exc
        return self._model

    def _config(self) -> OmniVoiceConfig:
        return OmniVoiceConfig(
            model_name=os.getenv("OMNIVOICE_MODEL", "k2-fsa/OmniVoice").strip() or "k2-fsa/OmniVoice",
            device_map=os.getenv("OMNIVOICE_DEVICE", "auto").strip() or "auto",
            dtype=os.getenv("OMNIVOICE_DTYPE", "auto").strip().lower() or "auto",
            ref_audio=os.getenv("OMNIVOICE_REF_AUDIO_PATH", "").strip(),
            ref_text=os.getenv("OMNIVOICE_REF_TEXT", "").strip(),
            language_id=os.getenv("OMNIVOICE_LANGUAGE_ID", "zh").strip() or None,
            speed=float(os.getenv("OMNIVOICE_SPEED", "1.0")),
            num_step=int(os.getenv("OMNIVOICE_NUM_STEP", "16")),
        )

    @staticmethod
    def _resolve_dtype(torch: Any, dtype: str) -> Any:
        if dtype == "float16":
            return torch.float16
        if dtype == "bfloat16":
            return torch.bfloat16
        if dtype == "float32":
            return torch.float32
        return torch.float16 if torch.cuda.is_available() else torch.float32

    @staticmethod
    def _generation_config(num_step: int) -> Any:
        try:
            from omnivoice.models.omnivoice import OmniVoiceGenerationConfig
        except Exception as exc:
            raise OmniVoiceError(f"Failed to load OmniVoice generation config: {exc}") from exc
        return OmniVoiceGenerationConfig(num_step=num_step)

    @staticmethod
    def _wav_bytes(audio: Any) -> bytes:
        try:
            import soundfile as sf
        except Exception as exc:
            raise OmniVoiceError("soundfile is required for OmniVoice WAV output.") from exc

        buffer = io.BytesIO()
        try:
            sf.write(buffer, audio, 24000, format="WAV")
        except Exception as exc:
            raise OmniVoiceError(f"Failed to encode OmniVoice audio: {exc}") from exc
        return buffer.getvalue()
