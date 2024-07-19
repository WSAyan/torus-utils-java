package org.torusresearch.torusutils.helpers;

import static org.torusresearch.torusutils.TorusUtils.secp256k1N;

import com.google.gson.Gson;

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.torusresearch.torusutils.apis.APIUtils;
import org.torusresearch.torusutils.apis.Ecies;
import org.torusresearch.torusutils.apis.GetPubKeyOrKeyAssignRequestParams;
import org.torusresearch.torusutils.apis.JsonRPCResponse;
import org.torusresearch.torusutils.apis.KeyLookupResult;
import org.torusresearch.torusutils.apis.KeysRPCResponse;
import org.torusresearch.torusutils.types.GetOrSetNonceResult;
import org.torusresearch.torusutils.types.KeyType;
import org.torusresearch.torusutils.types.Point;
import org.torusresearch.torusutils.types.Polynomial;
import org.torusresearch.torusutils.types.Share;
import org.torusresearch.torusutils.types.VerifierLookupResponse;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class Utils {
    private Utils() {
    }

    public static String thresholdSame(String[] arr, int threshold) {
        HashMap<String, Integer> hashMap = new HashMap<>();
        for (String s : arr) {
            Integer currentCount = hashMap.get(s);
            if (currentCount == null) currentCount = 0;
            int incrementedCount = currentCount + 1;
            if (incrementedCount == threshold) {
                return s;
            }
            hashMap.put(s, currentCount + 1);
        }
        return null;
    }

    public static String thresholdSame(List<String> list, int threshold) {
        String[] arr = new String[list.size()];
        list.toArray(arr);
        return Utils.thresholdSame(arr, threshold);
    }

    public static List<List<Integer>> kCombinations(int s, int k) {
        List<Integer> set = new ArrayList<>();
        for (int i = 0; i < s; i++) {
            set.add(i);
        }
        return kCombinations(set, k);
    }

    public static List<List<Integer>> kCombinations(List<Integer> set, int k) {
        List<List<Integer>> combs = new ArrayList<>();
        if (k > set.size()) {
            return combs;
        }
        if (k == set.size()) {
            combs.add(set);
            return combs;
        }
        if (k == 1) {
            for (Integer i : set) {
                ArrayList<Integer> arrList = new ArrayList<>();
                arrList.add(i);
                combs.add(arrList);
            }
            return combs;
        }
        for (int i = 0; i < set.size() - k + 1; i++) {
            List<List<Integer>> tailCombs = Utils.kCombinations(set.subList(i + 1, set.size()), k - 1);
            for (List<Integer> tailComb : tailCombs) {
                List<Integer> prependedComb = new ArrayList<>();
                prependedComb.add(set.get(i));
                prependedComb.addAll(tailComb);
                combs.add(prependedComb);
            }
        }
        return combs;
    }

    public static CompletableFuture<KeyLookupResult> getPubKeyOrKeyAssign(String[] endpoints, String network, String verifier, String verifierId, KeyType keyType, String extendedVerifierId) {
        int k = endpoints.length / 2 + 1;
        List<CompletableFuture<String>> lookupPromises = new ArrayList<>();
        for (int i = 0; i < endpoints.length; i++) {
            lookupPromises.add(i, APIUtils.post(endpoints[i], APIUtils.generateJsonRPCObject("GetPubKeyOrKeyAssign",
                    new GetPubKeyOrKeyAssignRequestParams(verifier, verifierId, extendedVerifierId, keyType,
                            true, true, true)), false));
        }
        return new Some<>(lookupPromises, (lookupResults, resolved) -> {
            try {
                List<String> errorResults = new ArrayList<>();
                List<String> keyResults = new ArrayList<>();
                List<BigInteger> nodeIndexes = new ArrayList<>();
                GetOrSetNonceResult nonceResult = null;
                Gson gson = new Gson();
                for (String x : lookupResults) {
                    if (x != null && !x.equals("")) {
                        try {
                            JsonRPCResponse response = gson.fromJson(x, JsonRPCResponse.class);
                            JSONObject jsonObject = new JSONObject(Utils.convertToJsonObject(response.getResult()));
                            if (jsonObject.has("node_index")) {
                                jsonObject.remove("node_index");
                            }
                            if (jsonObject.has("keys")) {
                                JSONArray keysArray = (JSONArray) jsonObject.get("keys");
                                for (int i = 0; i < keysArray.length(); i++) {
                                    JSONObject keyObject = (JSONObject) keysArray.get(i);
                                    if (keyObject.has("key_index")) {
                                        keyObject.remove("key_index");
                                    }
                                    if (keyObject.has("created_at")) {
                                        keyObject.remove("created_at");
                                    }
                                }
                            }
                            keyResults.add(jsonObject.toString());
                        } catch (Exception e) {
                            keyResults.add("");
                        }
                    }
                }
                for (String x : lookupResults) {
                    if (x != null && !x.equals("")) {
                        try {
                            JsonRPCResponse response = gson.fromJson(x, JsonRPCResponse.class);
                            errorResults.add(response.getError().getData());
                        } catch (Exception e) {
                            errorResults.add("");
                        }
                    }
                }
                for (String x : lookupResults) {
                    if (x != null && !x.equals("")) {
                        try {
                            JsonRPCResponse response = gson.fromJson(x, JsonRPCResponse.class);
                            VerifierLookupResponse verifierLookupResponse = gson.fromJson(Utils.convertToJsonObject(response.getResult()), VerifierLookupResponse.class);
                            String pubNonceX = null;
                            if (verifierLookupResponse.getKeys().get(0).getNonceData() != null && !verifierLookupResponse.getKeys().get(0).getNonceData().equals("")) {
                                pubNonceX = verifierLookupResponse.getKeys().get(0).getNonceData().getPubNonce().getX();
                            }
                            if (pubNonceX != null) {
                                nonceResult = verifierLookupResponse.getKeys().get(0).getNonceData();
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }

                String errorResult = thresholdSame(errorResults, k);
                String keyResult = thresholdSame(keyResults, k);

                if (keyResult != null && nonceResult == null && extendedVerifierId == null && !org.torusresearch.fetchnodedetails.types.Utils.LEGACY_NETWORKS_ROUTE_MAP.containsKey(network)) {
                    for (String x1 : lookupResults) {
                        if (x1 != null && !x1.equals("")) {
                            JsonRPCResponse response = gson.fromJson(x1, JsonRPCResponse.class);
                            VerifierLookupResponse verifierLookupResponse = gson.fromJson(Utils.convertToJsonObject(response.getResult()), VerifierLookupResponse.class);
                            String currentNodePubKey = verifierLookupResponse.getKeys().get(0).getPubKeyX().toLowerCase();
                            String pubNonceX = verifierLookupResponse.getKeys().get(0).getNonceData().getPubNonce().getX();
                            String thresholdPubKey = null;
                            for (String x : keyResults) {
                                KeysRPCResponse keyResponse = gson.fromJson(x, KeysRPCResponse.class);
                                thresholdPubKey = keyResponse.getKeys().get(0).getPubKeyX().toLowerCase();
                            }
                            if (pubNonceX != null && currentNodePubKey.equals(thresholdPubKey)) {
                                nonceResult = verifierLookupResponse.getKeys().get(0).getNonceData();
                                break;
                            }
                        }
                    }
                }

                List<BigInteger> serverTimeOffsets = new ArrayList<>();
                if ((keyResult != null && (nonceResult != null || extendedVerifierId != null || org.torusresearch.fetchnodedetails.types.Utils.LEGACY_NETWORKS_ROUTE_MAP.containsKey(network)))
                        || errorResult != null) {
                    for (String x : lookupResults) {
                        JsonRPCResponse response = gson.fromJson(x, JsonRPCResponse.class);
                        VerifierLookupResponse verifierLookupResponse = gson.fromJson(Utils.convertToJsonObject(response.getResult()), VerifierLookupResponse.class);
                        String currentNodePubKey = verifierLookupResponse.getKeys().get(0).getPubKeyX().toLowerCase();
                        String serverTimeOffsetStr = verifierLookupResponse.getServerTimeOffset();
                        String thresholdPubKey = null;
                        for (String x1 : keyResults) {
                            KeysRPCResponse keyResponse = gson.fromJson(x1, KeysRPCResponse.class);
                            thresholdPubKey = keyResponse.getKeys().get(0).getPubKeyX().toLowerCase();
                        }
                        if (currentNodePubKey.equals(thresholdPubKey)) {
                            nodeIndexes.add(verifierLookupResponse.getNodeIndex());
                        }
                        BigInteger serverTimeOffset = BigInteger.valueOf(serverTimeOffsetStr != null ? Integer.parseInt(serverTimeOffsetStr, 10) : 0);
                        serverTimeOffsets.add(serverTimeOffset);
                    }
                    BigInteger serverTimeOffset = keyResult != null ? (BigInteger) calculateMedian(serverTimeOffsets) : BigInteger.ZERO;
                    return CompletableFuture.completedFuture(new KeyLookupResult(keyResult, errorResult, nodeIndexes, nonceResult, serverTimeOffset));
                }

                CompletableFuture<KeyLookupResult> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new Exception("invalid results from KeyLookup " + gson.toJson(lookupResults)));
                return failedFuture;
            } catch (Exception e) {
                return null;
            }
        }).getCompletableFuture();
    }

    public static boolean isEmpty(final CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public static String padLeft(String inputString, Character padChar, int length) {
        if (inputString.length() >= length) return inputString;
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length - inputString.length()) {
            sb.append(padChar);
        }
        sb.append(inputString);
        return sb.toString();
    }

    public static String stripPaddingLeft(String inputString, Character padChar) {
        StringBuilder sb = new StringBuilder(inputString);
        while (sb.length() > 1 && sb.charAt(0) == padChar) {
            sb.deleteCharAt(0);
        }
        return sb.toString();
    }

    public static String convertToJsonObject(Object obj) {
        Gson gson = new Gson();
        return obj == null ? "" : gson.toJson(obj);
    }

    public static ECPoint getPublicKeyFromHex(String X, String Y) {
        BigInteger x = new BigInteger(X, 16);
        BigInteger y = new BigInteger(Y, 16);
        ECNamedCurveParameterSpec curve = ECNamedCurveTable.getParameterSpec("secp256k1");
        return curve.getCurve().createPoint(x, y);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    public static boolean isSapphireNetwork(String network) {
        return network.contains("sapphire");
    }

    public static String getJsonRPCObjectMethodName(String network) {
        if (isSapphireNetwork(network)) {
            return "GetShareOrKeyAssign";
        } else {
            return "ShareRequest";
        }
    }

    private static final char[] DIGITS
            = {'0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static String getECDSASignature(BigInteger privateKey, String data) throws IOException {
        ECKeyPair derivedECKeyPair = ECKeyPair.create(privateKey);
        byte[] hashedData = Hash.sha3(data.getBytes(StandardCharsets.UTF_8));
        ECDSASignature signature = derivedECKeyPair.sign(hashedData);
        String sig = Utils.padLeft(signature.r.toString(16), '0', 64) + Utils.padLeft(signature.s.toString(16), '0', 64) + Utils.padLeft("", '0', 2);
        byte[] sigBytes = AES256CBC.toByteArray(new BigInteger(sig, 16));
        String finalSig = new String(Base64.encodeBytesToBytes(sigBytes), StandardCharsets.UTF_8);
        return finalSig;
    }

    public static String randomString(int len) {
        String charSet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(charSet.charAt(rnd.nextInt(charSet.length())));
        }
        return sb.toString();
    }

    public static String getPubKey(String sessionId) {
        ECKeyPair derivedECKeyPair = ECKeyPair.create(new BigInteger(sessionId, 16));
        return derivedECKeyPair.getPublicKey().toString(16);
    }

    public static String getPrivKey(String sessionId) {
        ECKeyPair derivedECKeyPair = ECKeyPair.create(new BigInteger(sessionId, 16));
        return derivedECKeyPair.getPrivateKey().toString(16);
    }

    public static BigInteger generatePrivate() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        return Keys.createEcKeyPair().getPrivateKey();
    }

    public static Polynomial generateRandomPolynomial(int degree, BigInteger secret, @Nullable List<Share> deterministicShares) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        BigInteger actualS = secret;
        if (secret == null) {
            actualS = generatePrivateExcludingIndexes(getBigIntegerList());
        }
        if (deterministicShares == null) {
            List<BigInteger> poly = new ArrayList<>();
            poly.add(actualS);
            for (int i = 0; i < degree; i++) {
                BigInteger share = generatePrivateExcludingIndexes(poly);
                poly.add(share);
            }
            return new Polynomial(poly.toArray(new BigInteger[0]));
        }

        if (deterministicShares.size() > degree) {
            throw new Error("deterministicShares in generateRandomPolynomial should be less or equal than degree to ensure an element of randomness");
        }
        Map<String, Point> points = new HashMap<>();
        for (Share share : deterministicShares) {
            points.put(share.getShareIndex().toString(), new Point(share.getShareIndex(), share.getShare()));
        }
        for (int i = 0; i < degree - deterministicShares.size(); i++) {
            BigInteger shareIndex = generatePrivateExcludingIndexes(getBigIntegerList());
            while (points.containsKey(shareIndex.toString(16))) {
                shareIndex = generatePrivateExcludingIndexes(getBigIntegerList());
            }
            points.put(shareIndex.toString(16), new Point(shareIndex, generatePrivate()));
        }
        points.put("0", new Point(BigInteger.ZERO, actualS));
        return lagrangeInterpolatePolynomial(new ArrayList<>(points.values()));
    }

    public static List<BigInteger> getBigIntegerList() {
        List<BigInteger> bigIntegersList = new ArrayList<>();

        for (int i = 0; i < 1; i++) {
            bigIntegersList.add(BigInteger.ZERO);
        }
        return bigIntegersList;
    }

    public static Polynomial lagrangeInterpolatePolynomial(List<Point> points) {
        return lagrange(points);
    }

    private static Polynomial lagrange(List<Point> unsortedPoints) {
        List<Point> sortedPoints = pointSort(unsortedPoints);
        BigInteger[] polynomial = generateEmptyBNArray(sortedPoints.size());
        for (int i = 0; i < sortedPoints.size(); i++) {
            BigInteger[] coefficients = interpolationPoly(i, sortedPoints);
            for (int k = 0; k < sortedPoints.size(); k++) {
                BigInteger tmp = sortedPoints.get(i).getY();
                tmp = tmp.multiply(coefficients[k]);
                polynomial[k] = polynomial[k].add(tmp).mod(secp256k1N);
            }
        }
        return new Polynomial(polynomial);
    }

    private static BigInteger[] interpolationPoly(int i, List<Point> innerPoints) {
        BigInteger[] coefficients = generateEmptyBNArray(innerPoints.size());
        BigInteger d = denominator(i, innerPoints);
        if (d.compareTo(BigInteger.ZERO) == 0) {
            throw new ArithmeticException("Denominator for interpolationPoly is 0");
        }
        coefficients[0] = d.modInverse(secp256k1N);
        for (int k = 0; k < innerPoints.size(); k++) {
            BigInteger[] newCoefficients = generateEmptyBNArray(innerPoints.size());
            if (k != i) {
                int j;
                if (k < i) {
                    j = k + 1;
                } else {
                    j = k;
                }
                j--;
                for (; j >= 0; j--) {
                    newCoefficients[j + 1] = newCoefficients[j + 1].add(coefficients[j]).mod(secp256k1N);
                    BigInteger tmp = innerPoints.get(k).getX();
                    tmp = tmp.multiply(coefficients[j]).mod(secp256k1N);
                    newCoefficients[j] = newCoefficients[j].subtract(tmp).mod(secp256k1N);
                }
                coefficients = newCoefficients;
            }
        }
        return coefficients;
    }

    private static BigInteger denominator(int i, List<Point> innerPoints) {
        BigInteger result = BigInteger.ONE;
        BigInteger xi = innerPoints.get(i).getX();
        for (int j = innerPoints.size() - 1; j >= 0; j--) {
            if (i != j) {
                BigInteger tmp = xi.subtract(innerPoints.get(j).getX());
                tmp = tmp.mod(secp256k1N);
                result = result.multiply(tmp).mod(secp256k1N);
            }
        }
        return result;
    }

    private static List<Point> pointSort(List<Point> innerPoints) {
        List<Point> pointArrClone = new ArrayList<>(innerPoints);
        pointArrClone.sort(Comparator.comparing(Point::getX));
        return pointArrClone;
    }

    private static BigInteger[] generateEmptyBNArray(int length) {
        BigInteger[] array = new BigInteger[length];
        for (int i = 0; i < length; i++) {
            array[i] = BigInteger.ZERO;
        }
        return array;
    }

    private static BigInteger generatePrivateExcludingIndexes(List<BigInteger> shareIndexes) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        BigInteger key = Keys.createEcKeyPair().getPrivateKey();
        for (BigInteger el : shareIndexes) {
            if (el.equals(key)) {
                return generatePrivateExcludingIndexes(shareIndexes);
            }
        }
        return key;
    }

    public static byte[] convertToByteArray(Object object) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(object);
        oos.flush();
        return bos.toByteArray();
    }

    public static Ecies encParamsBufToHex(Ecies encParams) {
        return new Ecies(
                bytesToHex(encParams.getIv().getBytes(StandardCharsets.UTF_8)),
                bytesToHex(encParams.getEphemPublicKey().getBytes(StandardCharsets.UTF_8)),
                bytesToHex(encParams.getCiphertext().getBytes(StandardCharsets.UTF_8)),
                bytesToHex(encParams.getMac().getBytes(StandardCharsets.UTF_8)),
                "AES256"
        );
    }

    public static String toHex(byte[] data) {
        final StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte datum : data) {
            sb.append(DIGITS[(datum >>> 4) & 0x0F]);
            sb.append(DIGITS[datum & 0x0F]);
        }
        return sb.toString();
    }

    public static String convertBase64ToHex(String base64String) throws IOException {
        byte[] decodedBytes = Base64.decode(base64String);
        return bytesToHex(decodedBytes);
    }

    public static byte[] fromHexString(final String encoded) {
        final byte[] result = new byte[encoded.length() / 2];
        final char[] enc = encoded.toCharArray();
        for (int i = 0; i < enc.length; i += 2) {
            StringBuilder curr = new StringBuilder(2);
            curr.append(enc[i]).append(enc[i + 1]);
            result[i / 2] = (byte) Integer.parseInt(curr.toString(), 16);
        }
        return result;
    }

    public static BigInteger calculateMedian(List<BigInteger> arr) {
        int arrSize = arr.size();

        if (arrSize == 0) return BigInteger.ZERO;

        Collections.sort(arr);

        // odd length
        if (arrSize % 2 != 0) {
            return arr.get(arrSize / 2);
        }

        // return average of two mid values in case of even arrSize
        BigInteger mid1 = arr.get(arrSize / 2 - 1);
        BigInteger mid2 = arr.get(arrSize / 2);
        return (mid1.add(mid2)).divide(BigInteger.valueOf(2));
    }

    public static ECParameterSpec getKeyCurve(KeyType keyType) {
        switch (keyType) {
            case ed25519:
                return getCurveParams("Ed25519");
            case secp256k1:
                return getCurveParams("secp256k1");
            default:
                throw new IllegalArgumentException("Invalid keyType: " + keyType);
        }
    }

    private static ECParameterSpec getCurveParams(String curveName) {
        Provider bcProvider = Security.getProvider("BC");
        if (bcProvider == null) {
            throw new IllegalStateException("BouncyCastle provider not found");
        }
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec(curveName);
        if (spec == null) {
            throw new IllegalArgumentException("Unsupported curve: " + curveName);
        }
        return new ECParameterSpec(spec.getCurve(), spec.getG(), spec.getN(), spec.getH(), spec.getSeed());
    }

    public static int getProxyCoordinatorEndpointIndex(String[] endpoints, String verifier, String verifierId) {
        String verifierIdStr = verifier + verifierId;
        byte[] hashedVerifierId = keccak256(verifierIdStr.getBytes(StandardCharsets.UTF_8));
        BigInteger hashedBigInt = new BigInteger(1, hashedVerifierId);
        BigInteger modResult = hashedBigInt.mod(BigInteger.valueOf(endpoints.length));
        return modResult.intValue();
    }

    private static byte[] keccak256(byte[] input) {
        Keccak.Digest256 digest = new Keccak.Digest256();
        return digest.digest(input);
    }

    public static byte[] getRandomBytes(int length) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    public static String addLeading0sForLength64(String input) {
        StringBuilder inputBuilder = new StringBuilder(input);
        while (inputBuilder.length() < 64) {
            inputBuilder.insert(0, "0");
        }
        input = inputBuilder.toString();
        return input;
    }

    public static String stripLeadingZeros(String hexString) {
        int len = hexString.length();
        int firstNonZero = 0;
        // Find the first non-zero character index
        while (firstNonZero < len - 1 && hexString.charAt(firstNonZero) == '0') {
            firstNonZero++;
        }
        return hexString.substring(firstNonZero);
    }

    public static String addLeadingZerosForLength64(String input) {
        int targetLength = 64;
        int inputLength = input.length();

        if (inputLength >= targetLength) {
            return input;
        } else {
            int numberOfZeros = targetLength - inputLength;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < numberOfZeros; i++) {
                sb.append('0');
            }
            sb.append(input);
            return sb.toString();
        }
    }

    public static String serializeHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static String strip04Prefix(String pubKey) {
        if (pubKey.startsWith("04")) {
            return pubKey.substring(2);
        }
        return pubKey;
    }

    public static String convertByteToHexadecimal(byte[] byteArray) {
        StringBuilder hex = new StringBuilder();
        // Iterating through each byte in the array
        for (byte b : byteArray) {
            hex.append(String.format("%02X", b));
        }
        return hex.toString().toLowerCase(Locale.ROOT);
    }

}
