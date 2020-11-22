/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.server.ce.service.sapldocument;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.sapl.api.interpreter.DocumentAnalysisResult;
import io.sapl.api.interpreter.DocumentType;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.reimpl.prp.PrpUpdateEvent;
import io.sapl.reimpl.prp.PrpUpdateEvent.Update;
import io.sapl.reimpl.prp.PrpUpdateEventSource;
import io.sapl.server.ce.model.sapldocument.SaplDocument;
import io.sapl.server.ce.model.sapldocument.SaplDocumentVersion;
import io.sapl.server.ce.persistence.SaplDocumentsRepository;
import io.sapl.server.ce.persistence.SaplDocumentsVersionRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ReplayProcessor;

/**
 * Service for reading and managing {@link SaplDocument} instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaplDocumentService implements PrpUpdateEventSource {
	private static final String DEFAULT_DOCUMENT_VALUE = "policy \"all deny\"\ndeny";

	private final SaplDocumentsRepository saplDocumentRepository;
	private final SaplDocumentsVersionRepository saplDocumentVersionRepository;
	private final SAPLInterpreter saplInterpreter;

	private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
			.withLocale(Locale.GERMANY).withZone(ZoneId.systemDefault());

	private FluxSink<PrpUpdateEvent> prpUpdateEventUpdateFluxSink;
	private Disposable prpUpdateEventUpdateMonitor;

	@Override
	public Flux<PrpUpdateEvent> getUpdates() {
		PrpUpdateEvent initialEvent = generateInitialPrpUpdateEvent();

		// @formatter:off
		ReplayProcessor<PrpUpdateEvent> prpUpdateEventUpdateProcessor = ReplayProcessor
				.<PrpUpdateEvent>create();
		prpUpdateEventUpdateFluxSink = prpUpdateEventUpdateProcessor.sink();
		Flux<PrpUpdateEvent> flux = prpUpdateEventUpdateProcessor.cache();
		prpUpdateEventUpdateMonitor = flux.subscribe();
		// @formatter:on

		log.debug("initial event: {}", initialEvent);
		return Mono.just(initialEvent).concatWith(flux);
	}

	/**
	 * Gets all {@link SaplDocument}s.
	 * 
	 * @return the instances
	 */
	public Collection<SaplDocument> getAll() {
		return this.saplDocumentRepository.findAll();
	}

	/**
	 * Gets a specific {@link SaplDocument} by its id.
	 * 
	 * @param id the id of the {@link SaplDocument}
	 * @return the {@link SaplDocument}
	 */
	public SaplDocument getById(long id) {
		Optional<SaplDocument> optionalEntity = this.saplDocumentRepository.findById(id);
		if (!optionalEntity.isPresent()) {
			throw new IllegalArgumentException(String.format("entity with id %d is not existing", id));
		}

		return optionalEntity.get();
	}

	/**
	 * Gets the amount of {@link SaplDocument}s.
	 * 
	 * @return the amount
	 */
	public long getAmount() {
		return this.saplDocumentRepository.count();
	}

	/**
	 * Creates a new {@link SaplDocument} with a default document value.
	 * 
	 * @return the created {@link SaplDocument}
	 */
	public SaplDocument createDefault() {
		String documentValue = DEFAULT_DOCUMENT_VALUE;

		DocumentAnalysisResult documentAnalysisResult = saplInterpreter.analyze(documentValue);

		DocumentType type = documentAnalysisResult.getType();
		String name = documentAnalysisResult.getName();

		SaplDocument saplDocumentToCreate = new SaplDocument().setLastModified(this.getCurrentTimestampAsString())
				.setName(name).setCurrentVersionNumber(1).setType(type);
		SaplDocument createdDocument = this.saplDocumentRepository.save(saplDocumentToCreate);

		SaplDocumentVersion initialSaplDocumentVersion = new SaplDocumentVersion().setSaplDocument(createdDocument)
				.setVersionNumber(1).setValue(documentValue).setName(name);
		this.saplDocumentVersionRepository.save(initialSaplDocumentVersion);

		return createdDocument;
	}

	/**
	 * Creates a new version of a {@link SaplDocument}.
	 * 
	 * @param saplDocumentId the id of the {@link SaplDocument} in which the version
	 *                       will be added
	 * @param documentValue  the document value of the version
	 * @return the created {@link SaplDocumentVersion}
	 */
	public SaplDocumentVersion createVersion(long saplDocumentId, @NonNull String documentValue) {
		SaplDocument saplDocument = this.getById(saplDocumentId);

		DocumentAnalysisResult documentAnalysisResult = saplInterpreter.analyze(documentValue);
		if (!documentAnalysisResult.isValid()) {
			throw new IllegalArgumentException(String.format("document value is invalid (value: %s)", documentValue));
		}

		int newVersionNumber = saplDocument.getCurrentVersionNumber() + 1;
		DocumentType type = documentAnalysisResult.getType();
		String newName = documentAnalysisResult.getName();

		SaplDocumentVersion newSaplDocumentVersion = new SaplDocumentVersion().setSaplDocument(saplDocument)
				.setVersionNumber(newVersionNumber).setValue(documentValue).setName(newName);
		this.saplDocumentVersionRepository.save(newSaplDocumentVersion);

		saplDocument.setCurrentVersionNumber(newVersionNumber).setLastModified(this.getCurrentTimestampAsString())
				.setType(type).setName(newName);
		this.saplDocumentRepository.save(saplDocument);

		return newSaplDocumentVersion;
	}

	/**
	 * Publishes a specific version of a {@link SaplDocument}.
	 * 
	 * @param saplDocumentId   the id of the {@link SaplDocument}
	 * @param versionToPublish the version to publish
	 * @throws PublishedDocumentNameCollisionException thrown if the name of a
	 *                                                 {@link SaplDocument} to
	 *                                                 publish is not unique
	 * 
	 */
	public void publishVersion(long saplDocumentId, int versionToPublish)
			throws PublishedDocumentNameCollisionException {
		SaplDocument saplDocument = this.getById(saplDocumentId);

		// unpublish other version if published
		if (saplDocument.getPublishedVersion() != null) {
			unpublishVersion(saplDocumentId);
		}

		SaplDocumentVersion saplDocumentVersionToPublish = saplDocument.getVersion(versionToPublish);

		this.checkForUniqueNameOfSaplDocumentToPublish(saplDocumentVersionToPublish.getName());

		// update document
		saplDocument.setPublishedVersion(saplDocumentVersionToPublish)
				.setLastModified(this.getCurrentTimestampAsString());
		this.saplDocumentRepository.save(saplDocument);

		log.info(String.format("publish version %d of SAPL document with id %d (name: %s)",
				saplDocumentVersionToPublish.getVersionNumber(), saplDocumentId,
				saplDocumentVersionToPublish.getName()));

		notifyAboutChangedPublicationOfSaplDocument(saplDocumentVersionToPublish, PrpUpdateEvent.Type.PUBLISH);
	}

	/**
	 * Unpublishes the published version of a {@link SaplDocument}.
	 * 
	 * @param saplDocumentId the id of the {@link SaplDocument}
	 */
	public void unpublishVersion(long saplDocumentId) {
		SaplDocument saplDocumentToUnpublish = this.getById(saplDocumentId);

		SaplDocumentVersion publishedVersion = saplDocumentToUnpublish.getPublishedVersion();
		if (publishedVersion == null) {
			return;
		}

		saplDocumentToUnpublish.setPublishedVersion(null);
		this.saplDocumentRepository.save(saplDocumentToUnpublish);

		log.info(String.format("unpublish version %d of SAPL document with id %d (name: %s)",
				publishedVersion.getVersionNumber(), saplDocumentId, saplDocumentToUnpublish.getName()));

		notifyAboutChangedPublicationOfSaplDocument(publishedVersion, PrpUpdateEvent.Type.UNPUBLISH);
	}

	@Override
	public void dispose() {
		if (prpUpdateEventUpdateMonitor != null) {
			prpUpdateEventUpdateMonitor.dispose();
		}
	}

	private String getCurrentTimestampAsString() {
		return this.dateFormatter.format(Instant.now());
	}

	private void checkForUniqueNameOfSaplDocumentToPublish(@NonNull String name)
			throws PublishedDocumentNameCollisionException {
		Collection<SaplDocument> saplDocumentsWithPublishedVersionWithEqualName = this.saplDocumentRepository
				.getSaplDocumentsByNameOfPublishedVersion(name);

		// throw appropriate exception for first conflicting SAPL document
		for (SaplDocument saplDocument : saplDocumentsWithPublishedVersionWithEqualName) {
			throw new PublishedDocumentNameCollisionException(saplDocument.getId(),
					saplDocument.getPublishedVersion().getVersionNumber());
		}
	}

	private PrpUpdateEvent generateInitialPrpUpdateEvent() {
		// @formatter:off
		List<PrpUpdateEvent.Update> updates = this.saplDocumentRepository.findAll()
				.stream()
				.filter(saplDocument -> saplDocument.getPublishedVersion() != null)
				.map(publishedSaplDocument -> convertSaplDocumentToUpdateOfPrpUpdateEvent(publishedSaplDocument.getPublishedVersion(), PrpUpdateEvent.Type.PUBLISH))
				.collect(Collectors.toList());
		// @formatter:on
		return new PrpUpdateEvent(updates);
	}

	private PrpUpdateEvent.Update convertSaplDocumentToUpdateOfPrpUpdateEvent(
			@NonNull SaplDocumentVersion publishedVersionOfSaplDocument,
			@NonNull PrpUpdateEvent.Type prpUpdateEventType) {
		SAPL sapl = saplInterpreter.parse(publishedVersionOfSaplDocument.getValue());
		return new Update(prpUpdateEventType, sapl, publishedVersionOfSaplDocument.getValue());
	}

	private void notifyAboutChangedPublicationOfSaplDocument(@NonNull SaplDocumentVersion saplDocumentVersion,
			PrpUpdateEvent.Type prpUpdateEventType) {
		PrpUpdateEvent.Update updateEvent = convertSaplDocumentToUpdateOfPrpUpdateEvent(saplDocumentVersion,
				prpUpdateEventType);
		prpUpdateEventUpdateFluxSink.next(new PrpUpdateEvent(updateEvent));
	}
}
