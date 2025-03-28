/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.simdvec;

import org.elasticsearch.simdvec.internal.vectorization.BaseVectorizationTests;
import org.elasticsearch.simdvec.internal.vectorization.ESVectorizationProvider;

import java.util.Arrays;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToLongBiFunction;

import static org.elasticsearch.simdvec.internal.vectorization.ESVectorUtilSupport.B_QUERY;
import static org.hamcrest.Matchers.closeTo;

public class ESVectorUtilTests extends BaseVectorizationTests {

    static final ESVectorizationProvider defaultedProvider = BaseVectorizationTests.defaultProvider();
    static final ESVectorizationProvider defOrPanamaProvider = BaseVectorizationTests.maybePanamaProvider();

    public void testIpByteBit() {
        byte[] q = new byte[16];
        byte[] d = new byte[] { (byte) Integer.parseInt("01100010", 2), (byte) Integer.parseInt("10100111", 2) };
        random().nextBytes(q);
        int expected = q[1] + q[2] + q[6] + q[8] + q[10] + q[13] + q[14] + q[15];
        assertEquals(expected, ESVectorUtil.ipByteBit(q, d));
    }

    public void testIpFloatBit() {
        float[] q = new float[16];
        byte[] d = new byte[] { (byte) Integer.parseInt("01100010", 2), (byte) Integer.parseInt("10100111", 2) };
        for (int i = 0; i < q.length; i++) {
            q[i] = random().nextFloat();
        }
        float expected = q[1] + q[2] + q[6] + q[8] + q[10] + q[13] + q[14] + q[15];
        assertEquals(expected, ESVectorUtil.ipFloatBit(q, d), 1e-6);
    }

    public void testIpFloatByte() {
        testIpFloatByteImpl(ESVectorUtil::ipFloatByte);
        testIpFloatByteImpl(defaultedProvider.getVectorUtilSupport()::ipFloatByte);
        testIpFloatByteImpl(defOrPanamaProvider.getVectorUtilSupport()::ipFloatByte);
    }

    private void testIpFloatByteImpl(ToDoubleBiFunction<float[], byte[]> impl) {
        int vectorSize = randomIntBetween(1, 1024);
        // scale the delta according to the vector size
        double delta = 1e-5 * vectorSize;

        float[] q = new float[vectorSize];
        byte[] d = new byte[vectorSize];
        for (int i = 0; i < q.length; i++) {
            q[i] = random().nextFloat();
        }
        random().nextBytes(d);

        float expected = 0;
        for (int i = 0; i < q.length; i++) {
            expected += q[i] * d[i];
        }
        assertThat(impl.applyAsDouble(q, d), closeTo(expected, delta));
    }

    public void testBitAndCount() {
        testBasicBitAndImpl(ESVectorUtil::andBitCountLong);
    }

    public void testIpByteBinInvariants() {
        int iterations = atLeast(10);
        for (int i = 0; i < iterations; i++) {
            int size = randomIntBetween(1, 10);
            var d = new byte[size];
            var q = new byte[size * B_QUERY - 1];
            expectThrows(IllegalArgumentException.class, () -> ESVectorUtil.ipByteBinByte(q, d));
        }
    }

    public void testBasicIpByteBin() {
        testBasicIpByteBinImpl(ESVectorUtil::ipByteBinByte);
        testBasicIpByteBinImpl(defaultedProvider.getVectorUtilSupport()::ipByteBinByte);
        testBasicIpByteBinImpl(defOrPanamaProvider.getVectorUtilSupport()::ipByteBinByte);
    }

    void testBasicBitAndImpl(ToLongBiFunction<byte[], byte[]> bitAnd) {
        assertEquals(0, bitAnd.applyAsLong(new byte[] { 0 }, new byte[] { 0 }));
        assertEquals(0, bitAnd.applyAsLong(new byte[] { 1 }, new byte[] { 0 }));
        assertEquals(0, bitAnd.applyAsLong(new byte[] { 0 }, new byte[] { 1 }));
        assertEquals(1, bitAnd.applyAsLong(new byte[] { 1 }, new byte[] { 1 }));
        byte[] a = new byte[31];
        byte[] b = new byte[31];
        random().nextBytes(a);
        random().nextBytes(b);
        int expected = scalarBitAnd(a, b);
        assertEquals(expected, bitAnd.applyAsLong(a, b));
    }

    void testBasicIpByteBinImpl(ToLongBiFunction<byte[], byte[]> ipByteBinFunc) {
        assertEquals(15L, ipByteBinFunc.applyAsLong(new byte[] { 1, 1, 1, 1 }, new byte[] { 1 }));
        assertEquals(30L, ipByteBinFunc.applyAsLong(new byte[] { 1, 2, 1, 2, 1, 2, 1, 2 }, new byte[] { 1, 2 }));

        var d = new byte[] { 1, 2, 3 };
        var q = new byte[] { 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3 };
        assert scalarIpByteBin(q, d) == 60L; // 4 + 8 + 16 + 32
        assertEquals(60L, ipByteBinFunc.applyAsLong(q, d));

        d = new byte[] { 1, 2, 3, 4 };
        q = new byte[] { 1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4 };
        assert scalarIpByteBin(q, d) == 75L; // 5 + 10 + 20 + 40
        assertEquals(75L, ipByteBinFunc.applyAsLong(q, d));

        d = new byte[] { 1, 2, 3, 4, 5 };
        q = new byte[] { 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5, 1, 2, 3, 4, 5 };
        assert scalarIpByteBin(q, d) == 105L; // 7 + 14 + 28 + 56
        assertEquals(105L, ipByteBinFunc.applyAsLong(q, d));

        d = new byte[] { 1, 2, 3, 4, 5, 6 };
        q = new byte[] { 1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6 };
        assert scalarIpByteBin(q, d) == 135L; // 9 + 18 + 36 + 72
        assertEquals(135L, ipByteBinFunc.applyAsLong(q, d));

        d = new byte[] { 1, 2, 3, 4, 5, 6, 7 };
        q = new byte[] { 1, 2, 3, 4, 5, 6, 7, 1, 2, 3, 4, 5, 6, 7, 1, 2, 3, 4, 5, 6, 7, 1, 2, 3, 4, 5, 6, 7 };
        assert scalarIpByteBin(q, d) == 180L; // 12 + 24 + 48 + 96
        assertEquals(180L, ipByteBinFunc.applyAsLong(q, d));

        d = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 };
        q = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 7, 8, 1, 2, 3, 4, 5, 6, 7, 8 };
        assert scalarIpByteBin(q, d) == 195L; // 13 + 26 + 52 + 104
        assertEquals(195L, ipByteBinFunc.applyAsLong(q, d));

        d = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
        q = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 1, 2, 3, 4, 5, 6, 7, 8, 9, 1, 2, 3, 4, 5, 6, 7, 8, 9, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
        assert scalarIpByteBin(q, d) == 225L; // 15 + 30 + 60 + 120
        assertEquals(225L, ipByteBinFunc.applyAsLong(q, d));
    }

    public void testIpByteBin() {
        testIpByteBinImpl(ESVectorUtil::ipByteBinByte);
        testIpByteBinImpl(defaultedProvider.getVectorUtilSupport()::ipByteBinByte);
        testIpByteBinImpl(defOrPanamaProvider.getVectorUtilSupport()::ipByteBinByte);
    }

    void testIpByteBinImpl(ToLongBiFunction<byte[], byte[]> ipByteBinFunc) {
        int iterations = atLeast(50);
        for (int i = 0; i < iterations; i++) {
            int size = random().nextInt(5000);
            var d = new byte[size];
            var q = new byte[size * B_QUERY];
            random().nextBytes(d);
            random().nextBytes(q);
            assertEquals(scalarIpByteBin(q, d), ipByteBinFunc.applyAsLong(q, d));

            Arrays.fill(d, Byte.MAX_VALUE);
            Arrays.fill(q, Byte.MAX_VALUE);
            assertEquals(scalarIpByteBin(q, d), ipByteBinFunc.applyAsLong(q, d));

            Arrays.fill(d, Byte.MIN_VALUE);
            Arrays.fill(q, Byte.MIN_VALUE);
            assertEquals(scalarIpByteBin(q, d), ipByteBinFunc.applyAsLong(q, d));
        }
    }

    static int scalarIpByteBin(byte[] q, byte[] d) {
        int res = 0;
        for (int i = 0; i < B_QUERY; i++) {
            res += (popcount(q, i * d.length, d, d.length) << i);
        }
        return res;
    }

    static int scalarBitAnd(byte[] a, byte[] b) {
        int res = 0;
        for (int i = 0; i < a.length; i++) {
            res += Integer.bitCount((a[i] & b[i]) & 0xFF);
        }
        return res;
    }

    public static int popcount(byte[] a, int aOffset, byte[] b, int length) {
        int res = 0;
        for (int j = 0; j < length; j++) {
            int value = (a[aOffset + j] & b[j]) & 0xFF;
            for (int k = 0; k < Byte.SIZE; k++) {
                if ((value & (1 << k)) != 0) {
                    ++res;
                }
            }
        }
        return res;
    }
}
