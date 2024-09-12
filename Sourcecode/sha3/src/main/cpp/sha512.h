#pragma once

#  define SHA_LONG unsigned int

#  define SHA_LBLOCK      16
#  define SHA_CBLOCK      (SHA_LBLOCK*4)/* SHA treats input data as a
                                         * contiguous array of 32 bit wide
                                         * big-endian values. */
#  define SHA_LAST_BLOCK  (SHA_CBLOCK-8)

#  define SHA512_CBLOCK   (SHA_LBLOCK*8)
#  if (defined(_WIN32) || defined(_WIN64)) && !defined(__MINGW32__)
#   define SHA_LONG64 unsigned __int64
#  elif defined(__arch64__)
#   define SHA_LONG64 unsigned long
#  else
#   define SHA_LONG64 unsigned long long
#  endif

# define SHA256_192_DIGEST_LENGTH 24
# define SHA224_DIGEST_LENGTH    28
# define SHA256_DIGEST_LENGTH    32
# define SHA384_DIGEST_LENGTH    48
# define SHA512_DIGEST_LENGTH    64


typedef struct SHA512state_st {
    SHA_LONG64 h[8];
    SHA_LONG64 Nl, Nh;
    union {
        SHA_LONG64 d[SHA_LBLOCK];
        unsigned char p[SHA512_CBLOCK];
    } u;
    unsigned int num, md_len;
} SHA512_CTX;

int SHA512_Init(SHA512_CTX* c);
int SHA512_Final(unsigned char* md, SHA512_CTX* c);
int SHA512_Update(SHA512_CTX* c, const void* _data, size_t len);
