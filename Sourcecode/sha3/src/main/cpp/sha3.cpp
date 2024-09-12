#include <jni.h>
#include <string>
#include <stdio.h>
#include "sha3_.h"

void hex2char(unsigned char* pHex, int nLen, char* p_pBuff) {
    for (int i = 0; i < nLen; i++) {
        char szBuff[3]; memset(szBuff, 0, sizeof(szBuff));
        sprintf(szBuff, "%02x", pHex[i]);
        strcat(p_pBuff, szBuff);
    }
}

void get_sha3_512(unsigned char* p_pMessage, int p_nLen, char* p_pHash) {

    KECCAK1600_CTX ctx;
    ossl_sha3_init(&ctx, '\x06', 512);
    unsigned char pBuff[32]; memset(pBuff, 1, sizeof(pBuff));
    ossl_sha3_update(&ctx, p_pMessage, p_nLen);
    unsigned char pHash[64]; memset(pHash, 0, sizeof(pHash));
    ossl_sha3_final(&ctx, pHash, 64);
    hex2char(pHash, 64, p_pHash);
}

void FillMemory_(unsigned char* p_pBuff, unsigned char* p_szPattern, int p_nLen, int p_nRepeat) {
    int nTotal = p_nLen * p_nRepeat;
    int nCurRepeat = 1;
    int nCopyNode;

    memcpy(p_pBuff, p_szPattern, p_nLen);

    while (nCurRepeat < p_nRepeat) {
        nCopyNode = (p_nRepeat - nCurRepeat) > nCurRepeat ? nCurRepeat : (p_nRepeat - nCurRepeat);
        memcpy(&p_pBuff[p_nLen * nCurRepeat], p_pBuff, nCopyNode * p_nLen);
        nCurRepeat += nCopyNode;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_anthroteacher_sha3_NativeLib_CalcHash(
        JNIEnv* env,
        jobject /* this */,
        jstring plain_text,
        jint repeat,
        jint level,
        jint truncate)
{
    const char *plain_text_buff = env->GetStringUTFChars(plain_text, nullptr);

    int nPatternLen = strlen(plain_text_buff) + 1;
    char* pPattern = (char*)malloc(nPatternLen+1);
    memset(pPattern, 0, nPatternLen+1);
    sprintf(pPattern, "%s\n", plain_text_buff);

    int nTailSize = 1 + nPatternLen;
    unsigned char* pTail = (unsigned char*)malloc(nTailSize + 1);
    memset(pTail, 0, nTailSize + 1);
    sprintf((char*)pTail, ": %s", plain_text_buff);

    // calc repeated plain text
    int nRepeatedPlainTextLen = nPatternLen * repeat + nTailSize;
    char* pRepeatedPlainText = (char*)malloc(nRepeatedPlainTextLen+1);
    FillMemory_((unsigned char*)pRepeatedPlainText, (unsigned char*)pPattern, nPatternLen, repeat);
    memcpy(&pRepeatedPlainText[nPatternLen * repeat], pTail, nTailSize);

    char pHashNow[130]; memset(pHashNow, 0, 130);
    get_sha3_512((unsigned char*)pRepeatedPlainText, nRepeatedPlainTextLen, pHashNow);
    //	sha3_512_hash((unsigned char*)pRepeatedPlainText, nRepeatedPlainTextLen, pHashNow);

    int nRepeatedLen = 129 * repeat + nTailSize;
    unsigned char* pRepeated = (unsigned char*)malloc(129 * repeat + nTailSize);

    for (int i = 1; i <= level; i++) {
        pHashNow[128] = '\n';
        FillMemory_(pRepeated, (unsigned char*)pHashNow, 129, repeat);
        memcpy(&pRepeated[129 * repeat], pTail, nTailSize);

        memset(pHashNow, 0, 130);
        get_sha3_512((unsigned char*)pRepeated, nRepeatedLen, pHashNow);
    }

    free(pPattern);
    free(pTail);
    free(pRepeatedPlainText);
    free(pRepeated);

    std::string result = pHashNow;
    std::transform(result.begin(), result.end(), result.begin(), ::toupper);
    return env->NewStringUTF(result.c_str());
}