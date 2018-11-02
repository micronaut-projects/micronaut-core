create table books (
  id int not null primary key,
  name varchar(255) not null,
  constraint id unique (id),
  constraint name unique (name),
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
