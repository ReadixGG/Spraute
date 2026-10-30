package org.sprautestudio.st_addon_spraute.engine.client;

import com.mojang.blaze3d.audio.OggAudioStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.lwjgl.BufferUtils;
import org.sprautestudio.st_addon_spraute.St_addon_spraute;
import org.sprautestudio.st_addon_spraute.editor.project.Project;
import org.sprautestudio.st_addon_spraute.editor.project.Workspace;
import org.sprautestudio.st_addon_spraute.runtime.Images;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The project's own sounds (its "sounds" folder: .ogg and .wav): played by the game's sound engine as streams read
 * straight from the files — no resource pack. In the world (a point, fading with distance; mono files only), at the
 * player, or as interface sounds; looped ones keep an id to be stopped.
 */
public final class ProjectSounds {
    private ProjectSounds() {
    }

    private static final Map<String, SoundInstance> PLAYING = new HashMap<>();
    private static final Map<String, Path> DIRS = new HashMap<>();

    /** The project's folder by its name. */
    private static Path projectDir(String project) {
        if (project == null || project.isEmpty()) return null;
        Path fromServer = org.sprautestudio.st_addon_spraute.content.ContentClient.dir(project);
        if (fromServer != null) return fromServer;
        Path direct = Workspace.projectsDir().resolve(project);
        if (Files.isDirectory(direct)) return direct;
        return DIRS.computeIfAbsent(project, n -> {
            for (Workspace.ProjectInfo i : Workspace.projects()) if (i.name().equals(n)) return i.dir();
            return null;
        });
    }

    /** The file of a project's sound, or null. */
    public static Path file(String project, String name) {
        Path dir = projectDir(project);
        // "minecraft:..." is a sound of the game, not a file; such names cannot be paths on Windows
        if (dir == null || name == null || name.isBlank() || name.contains(":")) return null;
        for (String ext : Project.SOUND_EXTENSIONS) {
            Path p = dir.resolve("sounds").resolve(name + ext);
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    public static boolean exists(String project, String name) {
        return file(project, name) != null;
    }

    /** A sound by a value of the settings or an interface: the project's own, else the game's id. */
    public static void ui(String project, String value) {
        if (value == null || value.isBlank()) return;
        String v = value.trim();
        if (exists(project, v)) {
            play(project, v, 0, 0, 0, false, 1, 1, false, "");
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(v.contains(":") ? v : "minecraft:" + v);
        if (rl != null) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(rl), 1f));
    }

    /** A sound of the mod itself, as heard from the interface: it takes the player's volume settings like any other. */
    public static void playBuiltin(String name) {
        ResourceLocation file = new ResourceLocation(St_addon_spraute.MODID, "sounds/ui/" + name + ".wav");
        Minecraft mc = Minecraft.getInstance();
        try (java.io.InputStream in = mc.getResourceManager().open(file)) {
            mc.getSoundManager().play(new Instance(new ResourceLocation(St_addon_spraute.MODID, "project_sound/builtin/" + name), in.readAllBytes(), true, 1, 1, false, false, 0, 0, 0));
        } catch (IOException e) {
            St_addon_spraute.LOGGER.warn("Spraute: no sound {}", file, e);
        }
    }

    /** Plays a project's sound; positional — at x, y, z in the world, else at the listener. */
    public static void play(String project, String name, double x, double y, double z, boolean positional, float volume, float pitch, boolean loop, String id) {
        Path f = file(project, name);
        if (f == null) {
            St_addon_spraute.LOGGER.warn("Spraute: sound «{}» not found in project {}", name, project);
            return;
        }
        byte[] data;
        try {
            data = Files.readAllBytes(f);
        } catch (IOException e) {
            St_addon_spraute.LOGGER.warn("Spraute: cannot read {}", f, e);
            return;
        }
        if (id != null && !id.isEmpty()) stop(id);
        Instance in = new Instance(new ResourceLocation("st_addon_spraute", "project_sound/" + Images.slug(project) + "/" + Images.slug(name)),
                data, f.getFileName().toString().toLowerCase().endsWith(".wav"), volume, pitch, loop, positional, x, y, z);
        Minecraft.getInstance().getSoundManager().play(in);
        if (id != null && !id.isEmpty()) PLAYING.put(id, in);
        else if (loop) PLAYING.put("~" + System.nanoTime(), in);
    }

    /** Stops the sound started with this id; "" — every looped project sound. */
    public static void stop(String id) {
        SoundManager sm = Minecraft.getInstance().getSoundManager();
        if (id == null || id.isEmpty()) {
            for (SoundInstance s : PLAYING.values()) sm.stop(s);
            PLAYING.clear();
            return;
        }
        SoundInstance s = PLAYING.remove(id);
        if (s != null) sm.stop(s);
    }

    /** A sound of a file: its own entry and its own stream. */
    private static final class Instance extends AbstractSoundInstance {
        private final byte[] data;
        private final boolean wav;

        Instance(ResourceLocation rl, byte[] data, boolean wav, float volume, float pitch, boolean loop, boolean positional, double x, double y, double z) {
            super(rl, SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.data = data;
            this.wav = wav;
            this.volume = volume;
            this.pitch = pitch;
            this.looping = loop;
            this.x = x;
            this.y = y;
            this.z = z;
            this.relative = !positional;
            this.attenuation = positional ? Attenuation.LINEAR : Attenuation.NONE;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            sound = new Sound(getLocation().toString(), ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 24);
            WeighedSoundEvents w = new WeighedSoundEvents(getLocation(), null);
            w.addSound(sound);
            return w;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary lib, Sound s, boolean looping) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    InputStream in = new ByteArrayInputStream(data);
                    AudioStream st = wav ? new WavStream(in) : new OggAudioStream(in);
                    return looping ? new Looping(st, this) : st;
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }, net.minecraft.Util.backgroundExecutor());
        }

        AudioStream reopen() throws IOException {
            InputStream in = new ByteArrayInputStream(data);
            return wav ? new WavStream(in) : new OggAudioStream(in);
        }
    }

    /** Starts over at the end (the engine's looping for streams). */
    private static final class Looping implements AudioStream {
        private AudioStream cur;
        private final Instance owner;

        Looping(AudioStream first, Instance owner) {
            this.cur = first;
            this.owner = owner;
        }

        @Override
        public AudioFormat getFormat() {
            return cur.getFormat();
        }

        @Override
        public ByteBuffer read(int size) throws IOException {
            ByteBuffer b = cur.read(size);
            if (b == null || !b.hasRemaining()) {
                cur.close();
                cur = owner.reopen();
                b = cur.read(size);
            }
            return b;
        }

        @Override
        public void close() throws IOException {
            cur.close();
        }
    }

    /** A WAV file as 16-bit PCM for OpenAL. */
    private static final class WavStream implements AudioStream {
        private final AudioInputStream in;
        private final AudioFormat format;

        WavStream(InputStream raw) throws IOException {
            try {
                AudioInputStream src = AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
                AudioFormat f = src.getFormat();
                AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16, Math.min(2, f.getChannels()),
                        Math.min(2, f.getChannels()) * 2, f.getSampleRate(), false);
                in = AudioSystem.getAudioInputStream(pcm, src);
                format = pcm;
            } catch (Exception e) {
                throw new IOException("WAV не читается: " + e.getMessage(), e);
            }
        }

        @Override
        public AudioFormat getFormat() {
            return format;
        }

        @Override
        public ByteBuffer read(int size) throws IOException {
            int frame = format.getFrameSize();
            byte[] buf = new byte[Math.max(frame, size - size % frame)];
            int n = 0;
            while (n < buf.length) {
                int r = in.read(buf, n, buf.length - n);
                if (r < 0) break;
                n += r;
            }
            n -= n % frame;
            ByteBuffer out = BufferUtils.createByteBuffer(n);
            out.put(buf, 0, n).flip();
            return out;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
