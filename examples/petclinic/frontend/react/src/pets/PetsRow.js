import React from 'react'
import PetsCell from "./PetsCell";

const PetsRow = ({pets, match}) => <div className="row">{pets.map((pet, i) => <PetsCell key={i} pet={pet} match={match}/>)}</div>

export default PetsRow