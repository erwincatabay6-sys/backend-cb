CREATE TABLE public.user_profile_images (
    user_id BIGINT PRIMARY KEY
        REFERENCES public.users(id) ON DELETE CASCADE,

    image_data BYTEA NOT NULL,

    content_type VARCHAR(50) NOT NULL,

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT user_profile_images_type_check
        CHECK (content_type IN ('image/jpeg', 'image/png')),

    CONSTRAINT user_profile_images_size_check
        CHECK (
            octet_length(image_data) > 0
            AND octet_length(image_data) <= 2097152
        )
);

GRANT SELECT, INSERT, UPDATE
ON TABLE public.user_profile_images
TO cellbank_app;