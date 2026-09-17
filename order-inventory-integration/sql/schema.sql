drop table if exists notifications cascade;
drop table if exists order_items cascade;
drop table if exists orders cascade;
drop table if exists inventory cascade;

create table inventory (
    product_id  text primary key,
    name        text not null,
    stock       integer not null check (stock >= 0)
);

create table orders (
    order_id    bigint generated always as identity primary key,
    status      text not null check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED')),
    reason      text,
    created_at  timestamptz not null default now()
);

create table order_items (
    order_item_id bigint generated always as identity primary key,
    order_id      bigint not null references orders (order_id) on delete cascade,
    product_id    text not null references inventory (product_id),
    quantity      integer not null check (quantity > 0)
);

create table notifications (
    notification_id bigint generated always as identity primary key,
    message          text not null,
    created_at       timestamptz not null default now()
);

insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 6),
    ('P300', 'USB-C Hub', 0)
on conflict (product_id) do update
    set name = excluded.name, stock = excluded.stock;