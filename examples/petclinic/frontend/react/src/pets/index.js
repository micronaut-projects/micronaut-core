import React from 'react';
import PetsGrid from "./PetsGrid";

const Pets = () => <div>
    <h2>Pets</h2>
    <PetsGrid pets={
        [{"vendor":"Arthur","name":"Hermione","image":"photo-1446231855385-1d4b0f025248.jpeg","slug":"hermione","type":"DOG"},{"vendor":"Arthur","name":"Goyle","image":"photo-1505481354248-2ba5d3b9338e.jpeg","slug":"goyle","type":"CAT"},{"vendor":"Fred","name":"Malfoy","image":"photo-1489911646836-b08d6ca60ffe.jpeg","slug":"malfoy","type":"CAT"},{"vendor":"Fred","name":"Ron","image":"photo-1442605527737-ed62b867591f.jpeg","slug":"ron","type":"DOG"},{"vendor":"Arthur","name":"Crabbe","image":"photo-1512616643169-0520ad604fc2.jpeg","slug":"crabbe","type":"CAT"},{"vendor":"Fred","name":"Harry","image":"photo-1457914109735-ce8aba3b7a79.jpeg","slug":"harry","type":"DOG"},{"vendor":"Arthur","name":"Crabbe","image":"photo-1512616643169-0520ad604fc2.jpeg","slug":"crabbe","type":"CAT"},{"vendor":"Fred","name":"Malfoy","image":"photo-1489911646836-b08d6ca60ffe.jpeg","slug":"malfoy","type":"CAT"},{"vendor":"Fred","name":"Ron","image":"photo-1442605527737-ed62b867591f.jpeg","slug":"ron","type":"DOG"},{"vendor":"Fred","name":"Harry","image":"photo-1457914109735-ce8aba3b7a79.jpeg","slug":"harry","type":"DOG"},{"vendor":"Arthur","name":"Goyle","image":"photo-1505481354248-2ba5d3b9338e.jpeg","slug":"goyle","type":"CAT"},{"vendor":"Arthur","name":"Hermione","image":"photo-1446231855385-1d4b0f025248.jpeg","slug":"hermione","type":"DOG"}]}/>
</div>

export default Pets;