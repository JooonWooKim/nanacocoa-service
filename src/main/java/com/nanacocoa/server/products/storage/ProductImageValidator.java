package com.nanacocoa.server.products.storage;

import com.nanacocoa.server.common.exception.NanacocoaException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

import static com.nanacocoa.server.common.exception.ErrorCode.INVALID_PRODUCT_IMAGE;
import static com.nanacocoa.server.common.exception.ErrorCode.PRODUCT_IMAGE_TOO_LARGE;

@Component
public class ProductImageValidator {
	static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

	public ProductImageFormat validate(MultipartFile image) {
		if (image == null || image.isEmpty()) {
			throw new NanacocoaException(INVALID_PRODUCT_IMAGE);
		}

		if (image.getSize() > MAX_IMAGE_SIZE) {
			throw new NanacocoaException(PRODUCT_IMAGE_TOO_LARGE);
		}

		byte[] signature = readSignature(image);
		if (isJpeg(signature)) {
			return ProductImageFormat.JPEG;
		}
		if (isPng(signature)) {
			return ProductImageFormat.PNG;
		}
		if (isWebp(signature)) {
			return ProductImageFormat.WEBP;
		}

		throw new NanacocoaException(INVALID_PRODUCT_IMAGE);
	}

	private byte[] readSignature(MultipartFile image) {
		try (InputStream inputStream = image.getInputStream()) {
			return inputStream.readNBytes(12);
		} catch (IOException e) {
			throw new NanacocoaException(INVALID_PRODUCT_IMAGE);
		}
	}

	private boolean isJpeg(byte[] bytes) {
		return bytes.length >= 3
				&& unsigned(bytes[0]) == 0xff
				&& unsigned(bytes[1]) == 0xd8
				&& unsigned(bytes[2]) == 0xff;
	}

	private boolean isPng(byte[] bytes) {
		int[] png = {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
		if (bytes.length < png.length) {
			return false;
		}
		for (int index = 0; index < png.length; index++) {
			if (unsigned(bytes[index]) != png[index]) {
				return false;
			}
		}
		return true;
	}

	private boolean isWebp(byte[] bytes) {
		return bytes.length >= 12
				&& matchesAscii(bytes, 0, "RIFF")
				&& matchesAscii(bytes, 8, "WEBP");
	}

	private boolean matchesAscii(byte[] bytes, int offset, String value) {
		for (int index = 0; index < value.length(); index++) {
			if (bytes[offset + index] != (byte) value.charAt(index)) {
				return false;
			}
		}
		return true;
	}

	private int unsigned(byte value) {
		return value & 0xff;
	}
}
