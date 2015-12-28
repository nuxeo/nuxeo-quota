/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Thomas Roger <troger@nuxeo.com>
 */

package org.nuxeo.ecm.quota;

import static org.nuxeo.ecm.core.api.LifeCycleConstants.TRANSITION_EVENT;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.ABOUT_TO_REMOVE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.ABOUT_TO_REMOVE_VERSION;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.BEFORE_DOC_RESTORE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.BEFORE_DOC_UPDATE;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CHECKEDIN;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CHECKEDOUT;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED_BY_COPY;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_MOVED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_RESTORED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_UPDATED;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.versioning.VersioningService;
import org.nuxeo.ecm.quota.size.QuotaExceededException;

/**
 * Abstract class implementing {@code QuotaStatsUpdater} to handle common cases.
 * <p>
 * Provides abstract methods to override for common events.
 *
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 * @since 5.5
 */
public abstract class AbstractQuotaStatsUpdater implements QuotaStatsUpdater {

    protected String name;

    protected String label;

    protected String descriptionLabel;

    protected static Log log = LogFactory.getLog(AbstractQuotaStatsUpdater.class);

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public void setDescriptionLabel(String descriptionLabel) {
        this.descriptionLabel = descriptionLabel;
    }

    @Override
    public String getDescriptionLabel() {
        return descriptionLabel;
    }

    @Override
    public void updateStatistics(CoreSession session, DocumentEventContext docCtx, Event event) {
        DocumentModel doc = docCtx.getSourceDocument();

        if (!needToProcessEventOnDocument(event, doc)) {
            log.debug("Exit Listener !!!!");
            return;
        }

        String eventName = event.getName();

        try {
            if (DOCUMENT_CREATED.equals(eventName)) {
                processDocumentCreated(session, doc, docCtx);
            } else if (ABOUT_TO_REMOVE.equals(eventName) || ABOUT_TO_REMOVE_VERSION.equals(eventName)) {
                processDocumentAboutToBeRemoved(session, doc, docCtx);
            } else if (DOCUMENT_CREATED_BY_COPY.equals(eventName)) {
                processDocumentCopied(session, doc, docCtx);
            } else if (DOCUMENT_MOVED.equals(eventName)) {
                DocumentRef sourceParentRef = (DocumentRef) docCtx.getProperty(CoreEventConstants.PARENT_PATH);
                DocumentModel sourceParent = session.getDocument(sourceParentRef);
                processDocumentMoved(session, doc, sourceParent, docCtx);
            } else if (DOCUMENT_UPDATED.equals(eventName)) {
                processDocumentUpdated(session, doc, docCtx);
            } else if (BEFORE_DOC_UPDATE.equals(eventName)) {
                processDocumentBeforeUpdate(session, doc, docCtx);
            } else if (TRANSITION_EVENT.equals(eventName)) {
                processDocumentTrashOp(session, doc, docCtx);
            } else if (DOCUMENT_CHECKEDIN.equals(eventName)) {
                processDocumentCheckedIn(session, doc, docCtx);
            } else if (DOCUMENT_CHECKEDOUT.equals(eventName)) {
                processDocumentCheckedOut(session, doc, docCtx);
            } else if (DOCUMENT_RESTORED.equals(eventName)) {
                processDocumentRestored(session, doc, docCtx);
            } else if (BEFORE_DOC_RESTORE.equals(eventName)) {
                processDocumentBeforeRestore(session, doc, docCtx);
            }
        } catch (QuotaExceededException e) {
            handleQuotaExceeded(e, event);
            throw e;
        }
    }

    protected List<DocumentModel> getAncestors(CoreSession session, DocumentModel doc) {
        List<DocumentModel> ancestors = new ArrayList<DocumentModel>();
        if (doc != null && doc.getParentRef() != null) {
            doc = session.getDocument(doc.getParentRef());
            while (doc != null && !doc.getPath().isRoot()) {
                ancestors.add(doc);
                doc = session.getDocument(doc.getParentRef());
            }
        }
        return ancestors;
    }

    protected abstract void handleQuotaExceeded(QuotaExceededException e, Event event);

    protected abstract boolean needToProcessEventOnDocument(Event event, DocumentModel targetDoc);

    protected abstract void processDocumentCreated(CoreSession session, DocumentModel doc, DocumentEventContext docCtx);

    protected abstract void processDocumentCopied(CoreSession session, DocumentModel doc, DocumentEventContext docCtx);

    protected abstract void processDocumentCheckedIn(CoreSession session, DocumentModel doc, DocumentEventContext docCtx);

    protected abstract void processDocumentCheckedOut(CoreSession session, DocumentModel doc,
            DocumentEventContext docCtx);

    protected abstract void processDocumentUpdated(CoreSession session, DocumentModel doc, DocumentEventContext docCtx);

    protected abstract void processDocumentMoved(CoreSession session, DocumentModel doc, DocumentModel sourceParent,
            DocumentEventContext docCtx);

    protected abstract void processDocumentAboutToBeRemoved(CoreSession session, DocumentModel doc,
            DocumentEventContext docCtx);

    protected abstract void processDocumentBeforeUpdate(CoreSession session, DocumentModel targetDoc,
            DocumentEventContext docCtx);

    protected abstract void processDocumentTrashOp(CoreSession session, DocumentModel doc, DocumentEventContext docCtx);

    protected abstract void processDocumentRestored(CoreSession session, DocumentModel doc, DocumentEventContext docCtx);

    protected abstract void processDocumentBeforeRestore(CoreSession session, DocumentModel doc,
            DocumentEventContext docCtx);

}
