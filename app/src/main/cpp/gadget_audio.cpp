#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <sstream>
#include <string>
#include <vector>

#include <unistd.h>

namespace {

struct Probe {
    std::string codec;
    int sampleRate = 0;
    int channels = 0;
    int bitDepth = 0;
    int bitrate = 0;
    bool lossless = false;
    bool known = false;
    float replayTrack = NAN;
    float replayAlbum = NAN;
    std::string title, artist, album, albumArtist, genre, composer;
    std::string comment, copyright, lyrics, encoder;
    int year = 0;
    int track = 0;
    int disc = 0;
};

struct Reader {
    int fd = -1;
    int64_t size = 0;

    bool readAt(int64_t off, void* buf, size_t n) const {
        if (fd < 0 || off < 0 || n == 0) return false;
        if (size > 0 && off + static_cast<int64_t>(n) > size) return false;
        auto* p = static_cast<uint8_t*>(buf);
        size_t got = 0;
        while (got < n) {
            ssize_t r = pread(fd, p + got, n - got, off + static_cast<int64_t>(got));
            if (r <= 0) return false;
            got += static_cast<size_t>(r);
        }
        return true;
    }

    std::vector<uint8_t> readVec(int64_t off, size_t n) const {
        std::vector<uint8_t> v(n);
        if (!readAt(off, v.data(), n)) v.clear();
        return v;
    }

    uint32_t u32be(int64_t off) const {
        uint8_t b[4];
        if (!readAt(off, b, 4)) return 0;
        return (uint32_t(b[0]) << 24) | (uint32_t(b[1]) << 16) | (uint32_t(b[2]) << 8) | b[3];
    }

    uint32_t u32le(int64_t off) const {
        uint8_t b[4];
        if (!readAt(off, b, 4)) return 0;
        return uint32_t(b[0]) | (uint32_t(b[1]) << 8) | (uint32_t(b[2]) << 16) | (uint32_t(b[3]) << 24);
    }
};

void appendUtf8(std::string& o, uint32_t cp) {
    if (cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) cp = 0xFFFD;
    if (cp < 0x80) {
        o.push_back(static_cast<char>(cp));
    } else if (cp < 0x800) {
        o.push_back(static_cast<char>(0xC0 | (cp >> 6)));
        o.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else if (cp < 0x10000) {
        o.push_back(static_cast<char>(0xE0 | (cp >> 12)));
        o.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        o.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else {
        o.push_back(static_cast<char>(0xF0 | (cp >> 18)));
        o.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
        o.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        o.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    }
}

std::string utf16ToUtf8(const uint8_t* p, size_t n, bool be) {
    std::string o;
    if (n >= 2) {
        if (p[0] == 0xFF && p[1] == 0xFE) {
            be = false;
            p += 2;
            n -= 2;
        } else if (p[0] == 0xFE && p[1] == 0xFF) {
            be = true;
            p += 2;
            n -= 2;
        }
    }
    for (size_t i = 0; i + 1 < n; i += 2) {
        uint16_t u = be ? (uint16_t(p[i]) << 8 | p[i + 1]) : (p[i] | uint16_t(p[i + 1]) << 8);
        if (u == 0) break;
        if (u >= 0xD800 && u <= 0xDBFF && i + 3 < n) {
            uint16_t u2 = be ? (uint16_t(p[i + 2]) << 8 | p[i + 3]) : (p[i + 2] | uint16_t(p[i + 3]) << 8);
            if (u2 >= 0xDC00 && u2 <= 0xDFFF) {
                uint32_t cp = 0x10000u + ((uint32_t(u - 0xD800) << 10) | (u2 - 0xDC00));
                appendUtf8(o, cp);
                i += 2;
                continue;
            }
        }
        appendUtf8(o, u);
    }
    return o;
}

std::string decodeText(const uint8_t* p, size_t n) {
    if (n == 0) return {};
    uint8_t enc = p[0];
    p++;
    n--;
    if (enc == 0) {
        std::string o;
        o.reserve(n);
        for (size_t i = 0; i < n; i++) {
            if (p[i] == 0) break;
            appendUtf8(o, p[i]);
        }
        return o;
    }
    if (enc == 3) {
        size_t len = 0;
        while (len < n && p[len] != 0) len++;
        return std::string(reinterpret_cast<const char*>(p), len);
    }
    if (enc == 1) return utf16ToUtf8(p, n, false);
    if (enc == 2) return utf16ToUtf8(p, n, true);
    return std::string(reinterpret_cast<const char*>(p), n);
}

std::string trimCopy(std::string s) {
    while (!s.empty() && (s.back() == ' ' || s.back() == '\0' || s.back() == '\r' || s.back() == '\n')) s.pop_back();
    size_t i = 0;
    while (i < s.size() && (s[i] == ' ' || s[i] == '\r' || s[i] == '\n')) i++;
    return s.substr(i);
}

std::string lower(std::string s) {
    for (char& c : s) {
        if (c >= 'A' && c <= 'Z') c = static_cast<char>(c - 'A' + 'a');
    }
    return s;
}

float parseGain(const std::string& raw) {
    std::string s = trimCopy(raw);
    if (s.empty()) return NAN;
    char* end = nullptr;
    float v = std::strtof(s.c_str(), &end);
    if (end == s.c_str()) return NAN;
    return v;
}

void assignTag(Probe& p, const std::string& keyRaw, const std::string& valueRaw) {
    std::string key = lower(trimCopy(keyRaw));
    std::string value = trimCopy(valueRaw);
    if (value.empty() || key.empty()) return;
    if (value.size() > 16000) value.resize(16000);
    if (key == "title" || key == "tit2") p.title = value;
    else if (key == "artist" || key == "tpe1") p.artist = value;
    else if (key == "album" || key == "talb") p.album = value;
    else if (key == "albumartist" || key == "album artist" || key == "tpe2") p.albumArtist = value;
    else if (key == "genre" || key == "tcon") p.genre = value;
    else if (key == "composer" || key == "tcom") p.composer = value;
    else if (key == "comment" || key == "comm" || key == "description") {
        if (p.comment.empty()) p.comment = value;
    } else if (key == "copyright" || key == "tcop") p.copyright = value;
    else if (key == "lyrics" || key == "unsyncedlyrics" || key == "uslt") {
        if (p.lyrics.empty()) p.lyrics = value;
    } else if (key == "encoder" || key == "tenc" || key == "encodedby" || key == "tool") p.encoder = value;
    else if (key == "date" || key == "year" || key == "tyer" || key == "tdrc") {
        int y = 0;
        for (char c : value) {
            if (c >= '0' && c <= '9') y = y * 10 + (c - '0');
            else if (y > 0) break;
            if (y > 9999) break;
        }
        if (y > 1000 && y < 3000) p.year = y;
    } else if (key == "tracknumber" || key == "trck" || key == "track") {
        int n = 0;
        for (char c : value) {
            if (c >= '0' && c <= '9') n = n * 10 + (c - '0');
            else break;
        }
        if (n > 0 && n < 1000) p.track = n;
    } else if (key == "discnumber" || key == "tpos" || key == "disc") {
        int n = 0;
        for (char c : value) {
            if (c >= '0' && c <= '9') n = n * 10 + (c - '0');
            else break;
        }
        if (n > 0 && n < 100) p.disc = n;
    } else if (key == "replaygain_track_gain") p.replayTrack = parseGain(value);
    else if (key == "replaygain_album_gain") p.replayAlbum = parseGain(value);
}

void parseVorbisComments(const uint8_t* data, size_t n, Probe& p) {
    if (n < 8) return;
    auto u32 = [&](size_t off) -> uint32_t {
        if (off + 4 > n) return 0;
        return uint32_t(data[off]) | (uint32_t(data[off + 1]) << 8) | (uint32_t(data[off + 2]) << 16) |
               (uint32_t(data[off + 3]) << 24);
    };
    uint32_t vendorLen = u32(0);
    if (4 + vendorLen + 4 > n) return;
    size_t off = 4 + vendorLen;
    uint32_t count = u32(off);
    off += 4;
    if (count > 4096) count = 4096;
    for (uint32_t i = 0; i < count && off + 4 <= n; i++) {
        uint32_t len = u32(off);
        off += 4;
        if (len > n - off) break;
        std::string comment(reinterpret_cast<const char*>(data + off), len);
        off += len;
        auto eq = comment.find('=');
        if (eq == std::string::npos) continue;
        assignTag(p, comment.substr(0, eq), comment.substr(eq + 1));
    }
}

uint32_t synchsafe(const uint8_t* b) {
    return (uint32_t(b[0] & 0x7F) << 21) | (uint32_t(b[1] & 0x7F) << 14) | (uint32_t(b[2] & 0x7F) << 7) |
           uint32_t(b[3] & 0x7F);
}

void parseId3(const Reader& r, Probe& p) {
    uint8_t hdr[10];
    if (!r.readAt(0, hdr, 10)) return;
    if (std::memcmp(hdr, "ID3", 3) != 0) return;
    int ver = hdr[3];
    if (ver != 3 && ver != 4) return;
    uint32_t tagSize = synchsafe(hdr + 6);
    if (tagSize > 8 * 1024 * 1024) tagSize = 8 * 1024 * 1024;
    auto buf = r.readVec(10, tagSize);
    if (buf.empty()) return;
    size_t off = 0;
    const size_t n = buf.size();
    while (off + 10 <= n) {
        if (buf[off] == 0) break;
        char id[5] = {char(buf[off]), char(buf[off + 1]), char(buf[off + 2]), char(buf[off + 3]), 0};
        uint32_t frameSize = 0;
        if (ver == 4) frameSize = synchsafe(buf.data() + off + 4);
        else
            frameSize = (uint32_t(buf[off + 4]) << 24) | (uint32_t(buf[off + 5]) << 16) |
                        (uint32_t(buf[off + 6]) << 8) | buf[off + 7];
        off += 10;
        if (frameSize == 0 || off + frameSize > n) break;
        const uint8_t* fp = buf.data() + off;
        std::string sid(id);
        if (sid == "TXXX" && frameSize > 2) {
            uint8_t enc = fp[0];
            std::string decoded = decodeText(fp, frameSize);
            // decodeText consumes the encoding byte. Description and value are separated by NUL.
            // Re-split from raw for reliability.
            const uint8_t* body = fp + 1;
            size_t bodyN = frameSize - 1;
            if (enc == 0 || enc == 3) {
                size_t z = 0;
                while (z < bodyN && body[z] != 0) z++;
                std::string desc(reinterpret_cast<const char*>(body), z);
                size_t v = z + 1;
                if (v < bodyN) {
                    std::string val(reinterpret_cast<const char*>(body + v), bodyN - v);
                    assignTag(p, desc, val);
                }
            } else {
                assignTag(p, "comment", decoded);
            }
        } else if ((sid == "COMM" || sid == "USLT") && frameSize > 5) {
            std::string text = decodeText(fp, frameSize);
            // drop language prefix if present in decoded form; store whole remainder
            if (sid == "USLT") assignTag(p, "lyrics", text);
            else assignTag(p, "comment", text);
        } else if (sid.size() == 4 && sid[0] == 'T') {
            std::string text = decodeText(fp, frameSize);
            assignTag(p, sid, text);
        }
        off += frameSize;
    }
}

void parseMp3Frame(const Reader& r, Probe& p) {
    int64_t start = 0;
    uint8_t hdr[10];
    if (r.readAt(0, hdr, 10) && std::memcmp(hdr, "ID3", 3) == 0) {
        start = 10 + static_cast<int64_t>(synchsafe(hdr + 6));
    }
    int64_t limit = std::min(r.size, start + 256 * 1024);
    const size_t chunk = 64 * 1024;
    for (int64_t off = start; off + 4 < limit; off += static_cast<int64_t>(chunk - 4)) {
        auto buf = r.readVec(off, static_cast<size_t>(std::min<int64_t>(chunk, limit - off)));
        if (buf.size() < 4) break;
        for (size_t i = 0; i + 4 < buf.size(); i++) {
            if (buf[i] != 0xFF || (buf[i + 1] & 0xE0) != 0xE0) continue;
            int ver = (buf[i + 1] >> 3) & 0x3;
            int layer = (buf[i + 1] >> 1) & 0x3;
            int brIndex = (buf[i + 2] >> 4) & 0xF;
            int srIndex = (buf[i + 2] >> 2) & 0x3;
            if (ver == 1 || layer == 0 || brIndex == 0 || brIndex == 15 || srIndex == 3) continue;
            static const int brV1L3[16] = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
            static const int brV2L3[16] = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};
            static const int srM1[3] = {44100, 48000, 32000};
            static const int srM2[3] = {22050, 24000, 16000};
            static const int srM25[3] = {11025, 12000, 8000};
            int sr = 0;
            if (ver == 3) sr = srM1[srIndex];
            else if (ver == 2) sr = srM2[srIndex];
            else sr = srM25[srIndex];
            int br = (ver == 3) ? brV1L3[brIndex] : brV2L3[brIndex];
            if (layer != 1) {
                // layer bits: 01 = layer 3, 10 = layer 2, 11 = layer 1
                br = brV1L3[brIndex];
            }
            p.codec = "MP3";
            p.sampleRate = sr;
            p.bitrate = br * 1000;
            p.bitDepth = 0;
            p.channels = ((buf[i + 3] >> 6) & 0x3) == 3 ? 1 : 2;
            p.lossless = false;
            p.known = true;
            return;
        }
    }
}

void parseFlac(const Reader& r, Probe& p) {
    uint8_t mag[4];
    if (!r.readAt(0, mag, 4) || std::memcmp(mag, "fLaC", 4) != 0) return;
    int64_t off = 4;
    bool last = false;
    for (int blocks = 0; blocks < 64 && !last && off + 4 < r.size; blocks++) {
        uint8_t hdr[4];
        if (!r.readAt(off, hdr, 4)) break;
        last = (hdr[0] & 0x80) != 0;
        int type = hdr[0] & 0x7F;
        uint32_t len = (uint32_t(hdr[1]) << 16) | (uint32_t(hdr[2]) << 8) | hdr[3];
        if (len > 8 * 1024 * 1024) break;
        if (type == 0 && len >= 18) {
            auto s = r.readVec(off + 4, std::min<uint32_t>(len, 34));
            if (s.size() >= 18) {
                uint32_t sr = (uint32_t(s[10]) << 12) | (uint32_t(s[11]) << 4) | (s[12] >> 4);
                int ch = ((s[12] >> 1) & 0x7) + 1;
                int bps = ((s[12] & 0x1) << 4 | (s[13] >> 4)) + 1;
                p.sampleRate = static_cast<int>(sr);
                p.channels = ch;
                p.bitDepth = bps;
                p.codec = "FLAC";
                p.lossless = true;
                p.known = true;
                if (p.sampleRate > 0 && p.bitDepth > 0 && p.channels > 0) {
                    p.bitrate = p.sampleRate * p.bitDepth * p.channels;
                }
            }
        } else if (type == 4 && len > 0 && len < 2 * 1024 * 1024) {
            auto c = r.readVec(off + 4, len);
            if (!c.empty()) parseVorbisComments(c.data(), c.size(), p);
        }
        off += 4 + static_cast<int64_t>(len);
    }
}

void parseWav(const Reader& r, Probe& p) {
    uint8_t hdr[12];
    if (!r.readAt(0, hdr, 12)) return;
    if (std::memcmp(hdr, "RIFF", 4) != 0 || std::memcmp(hdr + 8, "WAVE", 4) != 0) return;
    int64_t off = 12;
    while (off + 8 < r.size && off < 2 * 1024 * 1024) {
        uint8_t ch[8];
        if (!r.readAt(off, ch, 8)) break;
        uint32_t len = uint32_t(ch[4]) | (uint32_t(ch[5]) << 8) | (uint32_t(ch[6]) << 16) | (uint32_t(ch[7]) << 24);
        std::string id(reinterpret_cast<char*>(ch), 4);
        if (id == "fmt " && len >= 16) {
            auto f = r.readVec(off + 8, std::min<uint32_t>(len, 64));
            if (f.size() >= 16) {
                int format = f[0] | (f[1] << 8);
                p.channels = f[2] | (f[3] << 8);
                p.sampleRate = int(f[4] | (f[5] << 8) | (f[6] << 16) | (f[7] << 24));
                int byteRate = int(f[8] | (f[9] << 8) | (f[10] << 16) | (f[11] << 24));
                p.bitDepth = f[14] | (f[15] << 8);
                p.bitrate = byteRate * 8;
                p.codec = (format == 1 || format == 3 || format == 0xFFFE) ? "WAV" : "WAV";
                p.lossless = format == 1 || format == 3 || format == 0xFFFE;
                p.known = true;
            }
        } else if ((id == "id3 " || id == "ID3 ") && len > 10 && len < 2 * 1024 * 1024) {
            // embedded id3 is rare; ignore if not at start
        }
        off += 8 + static_cast<int64_t>(len);
        if (len & 1) off++;
    }
}

void parseOgg(const Reader& r, Probe& p) {
    uint8_t mag[4];
    if (!r.readAt(0, mag, 4) || std::memcmp(mag, "OggS", 4) != 0) return;
    // Walk a few pages from the start looking for OpusHead / Vorbis.
    int64_t off = 0;
    for (int page = 0; page < 8 && off + 27 < r.size; page++) {
        uint8_t hdr[27];
        if (!r.readAt(off, hdr, 27) || std::memcmp(hdr, "OggS", 4) != 0) break;
        int segs = hdr[26];
        auto segTable = r.readVec(off + 27, static_cast<size_t>(segs));
        if (static_cast<int>(segTable.size()) != segs) break;
        size_t body = 0;
        for (int s = 0; s < segs; s++) body += segTable[s];
        auto data = r.readVec(off + 27 + segs, body);
        if (data.size() >= 8 && std::memcmp(data.data(), "OpusHead", 8) == 0 && data.size() >= 19) {
            p.channels = data[9];
            p.sampleRate = int(data[12] | (data[13] << 8) | (data[14] << 16) | (data[15] << 24));
            // Opus is always decoded at 48 kHz; pre-skip input rate is stored but output is 48000.
            if (p.sampleRate <= 0) p.sampleRate = 48000;
            p.codec = "Opus";
            p.lossless = false;
            p.bitDepth = 0;
            p.known = true;
        } else if (data.size() >= 7 && std::memcmp(data.data(), "\x01vorbis", 7) == 0 && data.size() >= 16) {
            p.channels = data[11];
            p.sampleRate = int(data[12] | (data[13] << 8) | (data[14] << 16) | (data[15] << 24));
            p.codec = "Vorbis";
            p.lossless = false;
            p.known = true;
        } else if (data.size() >= 7 && std::memcmp(data.data(), "\x03vorbis", 7) == 0) {
            if (data.size() > 7) parseVorbisComments(data.data() + 7, data.size() - 7, p);
        } else if (data.size() > 8 && std::memcmp(data.data(), "OpusTags", 8) == 0) {
            parseVorbisComments(data.data() + 8, data.size() - 8, p);
        }
        off += 27 + segs + static_cast<int64_t>(body);
    }
}

void walkMp4(const Reader& r, int64_t start, int64_t end, int depth, Probe& p) {
    if (depth > 8 || end - start < 8) return;
    int64_t off = start;
    int guard = 0;
    while (off + 8 <= end && guard++ < 4000) {
        uint32_t size32 = r.u32be(off);
        uint8_t typeb[4];
        if (!r.readAt(off + 4, typeb, 4)) break;
        std::string type(reinterpret_cast<char*>(typeb), 4);
        int64_t header = 8;
        int64_t atomSize = size32;
        if (size32 == 1) {
            if (off + 16 > end) break;
            uint32_t hi = r.u32be(off + 8);
            uint32_t lo = r.u32be(off + 12);
            atomSize = (static_cast<int64_t>(hi) << 32) | lo;
            header = 16;
        } else if (size32 == 0) {
            atomSize = end - off;
        }
        if (atomSize < header || off + atomSize > end) break;
        int64_t child = off + header;
        int64_t childEnd = off + atomSize;
        if (type == "moov" || type == "trak" || type == "mdia" || type == "minf" || type == "stbl" ||
            type == "udta" || type == "ilst" || type == "edts") {
            walkMp4(r, child, childEnd, depth + 1, p);
        } else if (type == "meta") {
            // meta has a 4-byte version/flags before children
            walkMp4(r, child + 4, childEnd, depth + 1, p);
        } else if (type == "mdhd" && childEnd - child >= 20) {
            uint8_t ver = 0;
            r.readAt(child, &ver, 1);
            if (ver == 0 && child + 20 <= childEnd) {
                p.sampleRate = static_cast<int>(r.u32be(child + 12));
            } else if (ver == 1 && child + 32 <= childEnd) {
                p.sampleRate = static_cast<int>(r.u32be(child + 20));
            }
        } else if (type == "stsd" && childEnd - child > 16) {
            auto sample = r.readVec(child + 8, std::min<int64_t>(childEnd - child - 8, 64));
            if (sample.size() >= 8) {
                // entry size(4) + format(4) after the count already skipped? 
                // stsd: version/flags(4) count(4) then entries. child points after atom header,
                // so child+8 is the first entry if we already skipped version? 
                // We passed child = atom+header, stsd payload starts with version(4)+count(4).
            }
            auto head = r.readVec(child, std::min<int64_t>(childEnd - child, 32));
            if (head.size() >= 16) {
                std::string fmt(reinterpret_cast<char*>(head.data() + 12), 4);
                if (fmt == "alac") {
                    p.codec = "ALAC";
                    p.lossless = true;
                    p.known = true;
                } else if (fmt == "flac" || fmt == "fLaC") {
                    p.codec = "FLAC";
                    p.lossless = true;
                    p.known = true;
                } else if (fmt == "mp4a") {
                    if (p.codec.empty()) {
                        p.codec = "AAC";
                        p.lossless = false;
                        p.known = true;
                    }
                } else if (fmt == "Opus") {
                    p.codec = "Opus";
                    p.lossless = false;
                    p.known = true;
                } else if (fmt == "twos" || fmt == "sowt" || fmt == "lpcm") {
                    p.codec = "PCM";
                    p.lossless = true;
                    p.known = true;
                }
            }
            // Look inside the sample entry for alac specific config and esds bitrate.
            int64_t entry = child + 8;
            if (entry + 8 < childEnd) {
                uint32_t esize = r.u32be(entry);
                if (esize >= 16 && entry + esize <= childEnd) {
                    walkMp4(r, entry + 16, entry + esize, depth + 1, p);
                }
            }
        } else if (type == "alac" && childEnd - child >= 28) {
            auto a = r.readVec(child, std::min<int64_t>(childEnd - child, 48));
            // After version/flags, ALAC specific config often starts at +4 or deeper.
            if (a.size() >= 24) {
                // Apple alac atom: 4 version + 4 unknown + config. Bit depth at offset 5 of config.
                // Be conservative: scan for a plausible bit depth.
                p.codec = "ALAC";
                p.lossless = true;
                p.known = true;
            }
        } else if (type == "esds" && childEnd - child > 16) {
            auto e = r.readVec(child, std::min<int64_t>(childEnd - child, 128));
            // average bitrate is near the end of DecoderConfigDescriptor; search for a plausible value
            if (e.size() > 8 && p.bitrate == 0) {
                for (size_t i = 4; i + 4 <= e.size(); i++) {
                    uint32_t v = (uint32_t(e[i]) << 24) | (uint32_t(e[i + 1]) << 16) | (uint32_t(e[i + 2]) << 8) | e[i + 3];
                    if (v >= 8000 && v <= 2000000 && (v % 100 == 0 || v > 32000)) {
                        p.bitrate = static_cast<int>(v);
                    }
                }
            }
        } else if (type.size() == 4 && (type[0] == '\xA9' || type == "trkn" || type == "disk" || type == "----")) {
            // ilst item: data atom follows
            if (type == "----") {
                // mean / name / data
                std::string name;
                int64_t c = child;
                while (c + 8 <= childEnd) {
                    uint32_t sz = r.u32be(c);
                    uint8_t tb[4];
                    if (!r.readAt(c + 4, tb, 4) || sz < 8 || c + sz > childEnd) break;
                    std::string ct(reinterpret_cast<char*>(tb), 4);
                    if (ct == "name" && sz > 12) {
                        auto nb = r.readVec(c + 12, sz - 12);
                        name.assign(reinterpret_cast<char*>(nb.data()), nb.size());
                    } else if (ct == "data" && sz > 16) {
                        auto db = r.readVec(c + 16, std::min<uint32_t>(sz - 16, 8000));
                        std::string val(reinterpret_cast<char*>(db.data()), db.size());
                        if (!name.empty()) assignTag(p, name, val);
                    }
                    c += sz;
                }
            } else {
                int64_t c = child;
                if (c + 8 <= childEnd) {
                    uint32_t sz = r.u32be(c);
                    uint8_t tb[4];
                    if (r.readAt(c + 4, tb, 4) && std::string(reinterpret_cast<char*>(tb), 4) == "data" && sz > 16) {
                        auto db = r.readVec(c + 16, std::min<uint32_t>(sz - 16, 16000));
                        std::string val(reinterpret_cast<char*>(db.data()), db.size());
                        // strip leading NULs common in iTunes data
                        size_t z = 0;
                        while (z < val.size() && val[z] == '\0') z++;
                        val = val.substr(z);
                        std::string key = type;
                        if (!key.empty() && static_cast<unsigned char>(key[0]) == 0xA9) key = key.substr(1);
                        if (type == "trkn" && db.size() >= 6) {
                            p.track = (db[2] << 8) | db[3];
                        } else if (type == "disk" && db.size() >= 6) {
                            p.disc = (db[2] << 8) | db[3];
                        } else {
                            static const char* map[][2] = {
                                {"nam", "title"}, {"ART", "artist"}, {"alb", "album"}, {"aART", "albumartist"},
                                {"gen", "genre"}, {"day", "date"},    {"wrt", "composer"}, {"cmt", "comment"},
                                {"lyr", "lyrics"}, {"too", "encoder"}, {"cpy", "copyright"},
                            };
                            std::string mapped = key;
                            for (auto& m : map) {
                                if (key == m[0]) mapped = m[1];
                            }
                            assignTag(p, mapped, val);
                        }
                    }
                }
            }
        }
        off += atomSize;
    }
}

void parseMp4(const Reader& r, Probe& p) {
    uint8_t mag[8];
    if (!r.readAt(4, mag, 4)) return;
    if (std::memcmp(mag, "ftyp", 4) != 0) return;
    walkMp4(r, 0, r.size, 0, p);
    if (p.codec.empty()) {
        p.codec = "M4A";
        p.known = true;
    }
    if (p.sampleRate > 1000000) {
        // mdhd timescale is not always the audio rate; leave it if plausible, else clear absurd values
        if (p.sampleRate > 384000) p.sampleRate = 0;
    }
}

void parseAacAdts(const Reader& r, Probe& p) {
    auto b = r.readVec(0, 8);
    if (b.size() < 7) return;
    if (b[0] != 0xFF || (b[1] & 0xF0) != 0xF0) return;
    static const int sr[16] = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
                               16000, 12000, 11025, 8000, 7350, 0, 0, 0};
    int idx = (b[2] >> 2) & 0xF;
    p.sampleRate = sr[idx];
    p.channels = ((b[2] & 0x1) << 2) | ((b[3] >> 6) & 0x3);
    p.codec = "AAC";
    p.lossless = false;
    p.known = true;
}

std::string jsonEscape(const std::string& in) {
    std::string o;
    o.reserve(in.size() + 8);
    const auto* s = reinterpret_cast<const unsigned char*>(in.data());
    for (size_t i = 0; i < in.size();) {
        unsigned char c = s[i];
        if (c < 0x80) {
            switch (c) {
                case '"': o += "\\\""; break;
                case '\\': o += "\\\\"; break;
                case '\n': o += "\\n"; break;
                case '\r': o += "\\r"; break;
                case '\t': o += "\\t"; break;
                default:
                    if (c < 0x20) {
                        char buf[8];
                        std::snprintf(buf, sizeof(buf), "\\u%04x", c);
                        o += buf;
                    } else {
                        o.push_back(static_cast<char>(c));
                    }
            }
            i++;
            continue;
        }
        int need = 0;
        if ((c & 0xE0) == 0xC0) need = 2;
        else if ((c & 0xF0) == 0xE0) need = 3;
        else if ((c & 0xF8) == 0xF0) need = 4;
        if (need == 0 || i + need > in.size()) {
            i++;
            continue;
        }
        bool ok = true;
        for (int k = 1; k < need; k++) {
            if ((s[i + k] & 0xC0) != 0x80) ok = false;
        }
        if (!ok) {
            i++;
            continue;
        }
        o.append(in, i, static_cast<size_t>(need));
        i += static_cast<size_t>(need);
    }
    return o;
}

std::string numOrNull(float v) {
    if (!std::isfinite(v)) return "null";
    char buf[32];
    std::snprintf(buf, sizeof(buf), "%.3f", v);
    return buf;
}

std::string toJson(const Probe& p) {
    std::ostringstream o;
    o << "{";
    auto str = [&](const char* k, const std::string& v, bool comma) {
        if (comma) o << ",";
        o << "\"" << k << "\":\"" << jsonEscape(v) << "\"";
    };
    str("codec", p.codec, false);
    o << ",\"sampleRate\":" << p.sampleRate;
    o << ",\"channels\":" << p.channels;
    o << ",\"bitDepth\":" << p.bitDepth;
    o << ",\"bitrate\":" << p.bitrate;
    o << ",\"lossless\":" << (p.lossless ? "true" : "false");
    o << ",\"known\":" << (p.known ? "true" : "false");
    o << ",\"replayGainTrack\":" << numOrNull(p.replayTrack);
    o << ",\"replayGainAlbum\":" << numOrNull(p.replayAlbum);
    str("title", p.title, true);
    str("artist", p.artist, true);
    str("album", p.album, true);
    str("albumArtist", p.albumArtist, true);
    str("genre", p.genre, true);
    str("composer", p.composer, true);
    str("comment", p.comment, true);
    str("copyright", p.copyright, true);
    str("lyrics", p.lyrics, true);
    str("encoder", p.encoder, true);
    o << ",\"year\":" << p.year;
    o << ",\"track\":" << p.track;
    o << ",\"disc\":" << p.disc;
    o << "}";
    return o.str();
}

std::string probeFd(int fd) {
    Probe p;
    if (fd < 0) return toJson(p);
    Reader r;
    r.fd = fd;
    r.size = lseek(fd, 0, SEEK_END);
    if (r.size < 0) r.size = 0;
    uint8_t mag[12] = {};
    r.readAt(0, mag, 12);
    if (std::memcmp(mag, "fLaC", 4) == 0) parseFlac(r, p);
    else if (std::memcmp(mag, "RIFF", 4) == 0) parseWav(r, p);
    else if (std::memcmp(mag, "OggS", 4) == 0) parseOgg(r, p);
    else if (std::memcmp(mag, "ID3", 3) == 0) {
        parseId3(r, p);
        parseMp3Frame(r, p);
    } else if (mag[0] == 0xFF && (mag[1] & 0xE0) == 0xE0) {
        parseMp3Frame(r, p);
        if (!p.known) parseAacAdts(r, p);
    } else if (r.size >= 8 && std::memcmp(mag + 4, "ftyp", 4) == 0) {
        parseMp4(r, p);
    } else if (std::memcmp(mag, "FORM", 4) == 0) {
        p.codec = "AIFF";
        p.lossless = true;
        p.known = true;
    }
    if (std::memcmp(mag, "ID3", 3) == 0 && p.title.empty()) parseId3(r, p);
    return toJson(p);
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_nazatric_thegadget_nativecore_NativeAudio_probe(JNIEnv* env, jobject, jint fd) {
    std::string json = probeFd(fd);
    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nazatric_thegadget_nativecore_NativeAudio_engineVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF("gadget-audio 1.0");
}
