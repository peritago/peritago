package com.skala.domainbridge.wiki.service;

public class WikiDocumentNotFoundException extends RuntimeException {

	public WikiDocumentNotFoundException(Long id) {
		super("wiki_documents에 id=" + id + "가 없습니다.");
	}
}
