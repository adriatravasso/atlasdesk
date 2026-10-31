package com.adria.atlasdesk.asset;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class AssetNotFoundException extends RuntimeException {

    public AssetNotFoundException() {
        super("Asset not found in the current organization");
    }
}
