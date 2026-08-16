INSERT INTO zip_code_location (
    zip_code,
    city,
    region,
    centroid
)
VALUES (
    '32539',
    'Crestview',
    'FL',
    ST_SetSRID(
        ST_MakePoint(-86.5705, 30.7621),
        4326
    )::geography
)
ON CONFLICT (zip_code) DO NOTHING;
