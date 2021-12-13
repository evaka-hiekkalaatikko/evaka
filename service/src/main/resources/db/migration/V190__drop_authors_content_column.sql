UPDATE curriculum_template SET content = jsonb_build_object(
    'sections', jsonb_build_array(CASE
        WHEN language = 'SV' THEN jsonb_build_object(
            'name', 'Uppgörande av barnets plan för småbarnspedagogik',
            'questions', jsonb_build_array(
                jsonb_build_object(
                    'type', 'MULTI_FIELD',
                    'ophKey', NULL,
                    'name', 'Person som ansvarat för uppgörande av planen',
                    'keys', jsonb_build_array(
                        jsonb_build_object('name', 'Förnamn'),
                        jsonb_build_object('name', 'Efternamn'),
                        jsonb_build_object('name', 'Titel'),
                        jsonb_build_object('name', 'Telefonnummer')
                    ),
                    'value', jsonb_build_array('', '', '', '')
                ),
                jsonb_build_object(
                    'type', 'MULTI_FIELD_LIST',
                    'ophKey', NULL,
                    'name', 'Övrig personal/sakkunniga som deltagit i uppgörandet av planen',
                    'keys', jsonb_build_array(
                        jsonb_build_object('name', 'Förnamn'),
                        jsonb_build_object('name', 'Efternamn'),
                        jsonb_build_object('name', 'Titel'),
                        jsonb_build_object('name', 'Telefonnummer')
                    ),
                    'value', jsonb_build_array()
                )
            )
        )
        ELSE jsonb_build_object(
            'name', 'Lapsen varhaiskasvatussuunnitelman laatijat',
            'questions', jsonb_build_array(
                jsonb_build_object(
                    'type', 'MULTI_FIELD',
                    'name', 'Laatimisesta vastaava henkilö',
                    'keys', jsonb_build_array(
                        jsonb_build_object('name', 'Etunimi'),
                        jsonb_build_object('name', 'Sukunimi'),
                        jsonb_build_object('name', 'Nimike'),
                        jsonb_build_object('name', 'Puhelinnumero')
                    ),
                    'value', jsonb_build_array('', '', '', '')
                ),
                jsonb_build_object(
                    'type', 'MULTI_FIELD_LIST',
                    'name', 'Muu laatimiseen osallistunut henkilöstö/asiantuntijat',
                    'keys', jsonb_build_array(
                        jsonb_build_object('name', 'Etunimi'),
                        jsonb_build_object('name', 'Sukunimi'),
                        jsonb_build_object('name', 'Nimike'),
                        jsonb_build_object('name', 'Puhelinnumero')
                    ),
                    'value', jsonb_build_array()
                )
            )
        )
    END) || (content->'sections')::jsonb
);

UPDATE curriculum_content SET content = jsonb_build_object(
    'sections', jsonb_build_array(CASE
        WHEN language = 'SV' THEN jsonb_build_object(
            'name', 'Uppgörande av barnets plan för småbarnspedagogik',
            'questions', jsonb_build_array(
                jsonb_build_object(
                    'type', 'MULTI_FIELD',
                    'ophKey', NULL,
                    'name', 'Person som ansvarat för uppgörande av planen',
                    'keys', jsonb_build_array(
                        jsonb_build_object('name', 'Förnamn'),
                        jsonb_build_object('name', 'Efternamn'),
                        jsonb_build_object('name', 'Titel'),
                        jsonb_build_object('name', 'Telefonnummer')
                    ),
                    'value', jsonb_build_array(
                        authors_content->'primaryAuthor'->>'name',
                        '',
                        authors_content->'primaryAuthor'->>'title',
                        authors_content->'primaryAuthor'->>'phone'
                    )
                ),
                jsonb_build_object(
                    'type', 'MULTI_FIELD_LIST',
                    'ophKey', NULL,
                    'name', 'Övrig personal/sakkunniga som deltagit i uppgörandet av planen',
                    'keys', jsonb_build_array(
                        jsonb_build_object('name', 'Förnamn'),
                        jsonb_build_object('name', 'Efternamn'),
                        jsonb_build_object('name', 'Titel'),
                        jsonb_build_object('name', 'Telefonnummer')
                    ),
                    'value', jsonb_build_array(
                        jsonb_build_array(
                            authors_content->'otherAuthors'->0->>'name',
                            '',
                            authors_content->'otherAuthors'->0->>'title',
                            authors_content->'otherAuthors'->0->>'phone'
                        )
                    )
                )
            )
        )
        ELSE jsonb_build_object(
            'name', 'Lapsen varhaiskasvatussuunnitelman laatijat',
            'questions', jsonb_build_array(
                jsonb_build_object(
                    'type', 'MULTI_FIELD',
                    'name', 'Laatimisesta vastaava henkilö',
                    'keys', jsonb_build_array(
                        jsonb_build_object('name', 'Etunimi'),
                        jsonb_build_object('name', 'Sukunimi'),
                        jsonb_build_object('name', 'Nimike'),
                        jsonb_build_object('name', 'Puhelinnumero')
                    ),
                    'value', jsonb_build_array(
                        authors_content->'primaryAuthor'->>'name',
                        '',
                        authors_content->'primaryAuthor'->>'title',
                        authors_content->'primaryAuthor'->>'phone'
                    )
                ),
                jsonb_build_object(
                    'type', 'MULTI_FIELD_LIST',
                    'name', 'Muu laatimiseen osallistunut henkilöstö/asiantuntijat',
                    'keys', jsonb_build_array(
                        jsonb_build_object('name', 'Etunimi'),
                        jsonb_build_object('name', 'Sukunimi'),
                        jsonb_build_object('name', 'Nimike'),
                        jsonb_build_object('name', 'Puhelinnumero')
                    ),
                    'value', jsonb_build_array(
                        jsonb_build_array(
                            authors_content->'otherAuthors'->0->>'name',
                            '',
                            authors_content->'otherAuthors'->0->>'title',
                            authors_content->'otherAuthors'->0->>'phone'
                        )
                    )
                )
            )
        )
    END) || (curriculum_content.content->'sections')::jsonb
)
FROM curriculum_document
JOIN curriculum_template ON curriculum_template.id = curriculum_document.template_id
WHERE curriculum_content.document_id = curriculum_document.id;

ALTER TABLE curriculum_content DROP COLUMN authors_content;
